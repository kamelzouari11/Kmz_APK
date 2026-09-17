mod github;
mod storage;

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .invoke_handler(tauri::generate_handler![
            github::github_request,
            storage::data_request
        ])
        .run(tauri::generate_context!())
        .expect("error while running Mes Placements");
}
