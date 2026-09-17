use base64::Engine;
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::{path::PathBuf, time::Duration};
use tauri::Manager;

const FILE: &str = "/contents/MySharedFolder/mes_placements_backup.json";

#[derive(Deserialize)]
pub struct GithubRequest {
    repository: String,
    path: String,
    method: String,
    body: Option<Value>,
}

#[derive(Serialize)]
pub struct GithubResponse {
    status: u16,
    body: Value,
}

fn read_token(app: &tauri::AppHandle) -> Result<String, String> {
    let project = PathBuf::from(env!("CARGO_MANIFEST_DIR")).join("..");
    let mut paths = vec![
        project.join("local.properties"),
        project.join("../local.properties"),
    ];
    if let Ok(dir) = app.path().app_config_dir() {
        paths.push(dir.join("local.properties"));
    }
    for path in paths {
        let text = match std::fs::read_to_string(path) {
            Ok(text) => text,
            Err(error) if error.kind() == std::io::ErrorKind::NotFound => continue,
            Err(_) => return Err("Impossible de lire local.properties.".into()),
        };
        for line in text
            .lines()
            .map(str::trim)
            .filter(|line| !line.starts_with('#'))
        {
            if let Some((key, value)) = line.split_once('=') {
                if key.trim() == "github.token" && !value.trim().is_empty() {
                    return Ok(value.trim().to_string());
                }
            }
        }
    }
    Err("Ajoutez github.token dans local.properties du projet ou du dossier de configuration de l’application.".into())
}

fn validate(request: &GithubRequest) -> Result<(), String> {
    let parts: Vec<_> = request.repository.split('/').collect();
    if parts.len() != 2
        || parts
            .iter()
            .any(|part| part.is_empty() || *part == "." || *part == "..")
        || !parts[0]
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || c == b'_' || c == b'-')
        || !parts[1]
            .bytes()
            .all(|c| c.is_ascii_alphanumeric() || b"_.-".contains(&c))
    {
        return Err("Dépôt GitHub invalide.".into());
    }
    let prefix = format!("{FILE}?ref=");
    if request.method == "GET"
        && (request.path.is_empty()
            || request
                .path
                .strip_prefix(&prefix)
                .is_some_and(|reference| !reference.contains('&')))
    {
        return Ok(());
    }
    if request.method == "PUT" && request.path == FILE {
        if let Some(content) = request.body.as_ref().and_then(|b| b["content"].as_str()) {
            if content.len() <= 1_200_000 {
                if let Ok(bytes) = base64::engine::general_purpose::STANDARD.decode(content) {
                    if let Ok(value) = serde_json::from_slice::<Value>(&bytes) {
                        if value["application"] == "mesplacements.encrypted"
                            && value["version"] == 1
                            && value["ciphertext"].is_string()
                        {
                            return Ok(());
                        }
                    }
                }
            }
        }
    }
    Err("Cette requête GitHub n’est pas autorisée.".into())
}

#[tauri::command]
pub async fn github_request(
    app: tauri::AppHandle,
    request: GithubRequest,
) -> Result<GithubResponse, String> {
    validate(&request)?;
    let token = read_token(&app)?;
    let client = reqwest::Client::builder()
        .timeout(Duration::from_secs(30))
        .redirect(reqwest::redirect::Policy::none())
        .build()
        .map_err(|_| "Connexion GitHub indisponible.")?;
    let url = format!(
        "https://api.github.com/repos/{}{}",
        request.repository, request.path
    );
    let method = if request.method == "PUT" {
        reqwest::Method::PUT
    } else {
        reqwest::Method::GET
    };
    let mut call = client
        .request(method, url)
        .bearer_auth(token)
        .header("Accept", "application/vnd.github+json")
        .header("X-GitHub-Api-Version", "2026-03-10")
        .header("User-Agent", "MesPlacements");
    if request.method == "PUT" {
        let body = request.body.as_ref().ok_or("Sauvegarde manquante.")?;
        let mut payload =
            json!({"message": "Sauvegarde Mes Placements", "content": body["content"]});
        for field in ["branch", "sha"] {
            if let Some(value) = body.get(field) {
                payload[field] = value.clone();
            }
        }
        call = call.json(&payload);
    }
    let response = call.send().await.map_err(|_| {
        "GitHub est injoignable. Vérifiez la connexion et le dépôt avant de réessayer."
    })?;
    let status = response.status().as_u16();
    let body = if response.status().is_success() {
        response
            .json()
            .await
            .map_err(|_| "Réponse GitHub invalide.")?
    } else {
        json!({})
    };
    Ok(GithubResponse { status, body })
}
