mod github;
mod storage;

#[tauri::command]
fn open_calculator() -> Result<(), String> {
    for command in ["gnome-calculator", "mate-calc", "galculator"] {
        match std::process::Command::new("sh")
            .args(["-c", &format!("command -v {command}")])
            .status()
        {
            Ok(status) if status.success() => {
                std::process::Command::new(command)
                    .spawn()
                    .map_err(|error| format!("Impossible d’ouvrir la calculatrice : {error}"))?;
                return Ok(());
            }
            _ => continue,
        }
    }
    Err("Aucune calculatrice système trouvée (gnome-calculator, mate-calc ou galculator).".into())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            github::github_request,
            storage::data_request,
            open_calculator
        ])
        .run(tauri::generate_context!())
        .expect("error while running Mes Placements");
}
