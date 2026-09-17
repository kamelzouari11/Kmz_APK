use serde::Deserialize;
use serde_json::{json, Value};
use std::{
    collections::HashSet,
    fs::{self, OpenOptions},
    io::Write,
    path::{Path, PathBuf},
    time::{SystemTime, UNIX_EPOCH},
};
use tauri::Manager;

#[derive(Deserialize)]
pub struct DataRequest {
    action: String,
    records: Option<Vec<Value>>,
    revision: Option<u64>,
}

fn validate(records: &[Value]) -> Result<(), String> {
    let mut ids = HashSet::new();
    for record in records {
        let id = record["id"]
            .as_str()
            .filter(|id| !id.is_empty())
            .ok_or("Identifiant manquant.")?;
        if !ids.insert(id) {
            return Err("Identifiant dupliqué.".into());
        }
        let year = record["exercice"].as_u64().ok_or("Exercice invalide.")?;
        if !(1900..=2100).contains(&year) {
            return Err("Exercice invalide.".into());
        }
        for field in [
            "etablissement",
            "placement",
            "revenu",
            "montant",
            "rs",
            "imposition",
            "declaration",
        ] {
            if record[field]
                .as_str()
                .is_none_or(|value| value.trim().is_empty())
            {
                return Err("Entrée incomplète.".into());
            }
        }
    }
    Ok(())
}
fn load(path: &Path) -> Result<Value, String> {
    let text = match fs::read_to_string(path) {
        Ok(text) => text,
        Err(error) if error.kind() == std::io::ErrorKind::NotFound => {
            return Ok(json!({"records": null, "revision": null, "path": path}))
        }
        Err(_) => return Err("Lecture du fichier local impossible.".into()),
    };
    let mut data: Value = serde_json::from_str(&text)
        .map_err(|_| "Fichier local endommagé. Aucune donnée écrasée.")?;
    if data["application"] != "mesplacements.local"
        || data["version"] != 1
        || data["revision"].as_u64().is_none_or(|r| r == 0)
    {
        return Err("Format du fichier local invalide. Aucune donnée écrasée.".into());
    }
    validate(
        data["records"]
            .as_array()
            .ok_or("Liste des entrées invalide.")?,
    )?;
    data["path"] = json!(path);
    Ok(data)
}
struct WriteGuard {
    lock: PathBuf,
    temporary: PathBuf,
}
impl Drop for WriteGuard {
    fn drop(&mut self) {
        let _ = fs::remove_file(&self.temporary);
        let _ = fs::remove_file(&self.lock);
    }
}
fn private_file(path: &Path) -> std::io::Result<std::fs::File> {
    let mut options = OpenOptions::new();
    options.write(true).create_new(true);
    #[cfg(unix)]
    {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    options.open(path)
}
#[tauri::command]
pub fn data_request(app: tauri::AppHandle, request: DataRequest) -> Result<Value, String> {
    let project = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let dir = if project.join("package.json").is_file() {
        project.join("data")
    } else {
        app.path()
            .app_data_dir()
            .map_err(|_| "Dossier de données introuvable.")?
            .join("data")
    };
    let path = dir.join("mesplacements.json");
    if request.action == "load" {
        return load(&path);
    }
    if request.action != "save" {
        return Err("Opération inconnue.".into());
    }
    let records = request.records.ok_or("Entrées manquantes.")?;
    validate(&records)?;
    fs::create_dir_all(&dir).map_err(|_| "Impossible de créer le dossier de données.")?;
    let lock = dir.join(".write-lock");
    let lock_file = private_file(&lock).map_err(|_| "Le fichier local est verrouillé. Réessayez ; après un arrêt brutal, vérifiez data/.write-lock.")?;
    let unique = format!(
        "{}-{}",
        std::process::id(),
        SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .map_err(|_| "Horloge invalide.")?
            .as_nanos()
    );
    let temporary = dir.join(format!(".pending-{unique}"));
    let _guard = WriteGuard {
        lock,
        temporary: temporary.clone(),
    };
    drop(lock_file);
    let previous = load(&path)?;
    if previous["revision"].as_u64() != request.revision {
        return Err("Les données ont changé dans une autre fenêtre. Copiez votre saisie puis rechargez avant de réessayer.".into());
    }
    let revision = request
        .revision
        .unwrap_or(0)
        .checked_add(1)
        .ok_or("Révision invalide.")?;
    let next = json!({"application": "mesplacements.local", "version": 1, "revision": revision, "records": records});
    if let Some(old) = request.revision {
        let backups = dir.join("backups");
        fs::create_dir_all(&backups).map_err(|_| "Impossible de conserver la copie précédente.")?;
        fs::copy(&path, backups.join(format!("revision-{old}-{unique}.json")))
            .map_err(|_| "Impossible de conserver la copie précédente.")?;
    }
    let mut file =
        private_file(&temporary).map_err(|_| "Impossible de créer le fichier temporaire.")?;
    file.write_all(
        serde_json::to_string_pretty(&next)
            .map_err(|_| "Données invalides.")?
            .as_bytes(),
    )
    .map_err(|_| "Écriture du fichier impossible.")?;
    file.sync_all()
        .map_err(|_| "Écriture du fichier non confirmée.")?;
    drop(file);
    fs::rename(&temporary, &path).map_err(|_| "Remplacement du fichier impossible.")?;
    let mut result = next;
    result["path"] = json!(path);
    Ok(result)
}
