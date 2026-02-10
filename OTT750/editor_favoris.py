import sqlite3
import shutil
import tkinter as tk
from tkinter import ttk, messagebox
import os

# Configuration
# Detect environment for paths
ANDROID_ROOT = '/storage/emulated/0/Download'
LOCAL_ROOT = '/home/kamel/OTT750'

if os.path.exists('/storage/emulated/0/'):
    DB_PATH = os.path.join(ANDROID_ROOT, 'database.db')
    NEW_DB_PATH = os.path.join(ANDROID_ROOT, 'database_new.db')
else:
    DB_PATH = os.path.join(LOCAL_ROOT, 'database.db')
    NEW_DB_PATH = os.path.join(LOCAL_ROOT, 'database_new.db')

# Mapping Satellites (Name -> ID)
SAT_MAP = {
    'Nilesat': 1,
    'Hotbird': 4,
    'Astra1': 5
}

# Mapping Favorites (User Label -> DB ID)
FAV_LABELS = ['Cinema', 'Sport', 'News', 'France', 'Italie', 'Nilesat']
FAV_MAP = {
    'Cinema': 1,
    'Sport': 2,
    'News': 3,
    'France': 4,
    'Italie': 5,
    'Nilesat': 6
}

# Symbols for checkbox state
CHECKED = "☒"
UNCHECKED = "☐"

class SatEditorApp:
    def __init__(self, root):
        self.root = root
        self.root.title("Éditeur de Favoris Satellite (Android Mode)")
        self.root.geometry("1200x800")

        self.conn = None
        self.cursor = None
        
        # Data Cache
        self.cache = {} # {sat_id: [ {id, name, favs: set()} ] }
        self.current_sat_id = None
        self.current_channels = [] # List of currently loaded channels for the satellite
        self.tree_item_map = {} # Map Treeview Item ID -> Index in self.current_channels (or filtered list)
        self.filtered_indices = [] # Indices of channels currently shown (after filter)

        self.apply_dark_theme()
        self.load_db_connection()
        self.setup_ui()

    def apply_dark_theme(self):
        style = ttk.Style()
        style.theme_use('clam')
        
        bg_color = "#1e1e1e"
        fg_color = "#ffffff"
        input_bg = "#2d2d2d"
        select_bg = "#007acc"
        
        self.root.configure(bg=bg_color)
        
        style.configure(".", background=bg_color, foreground=fg_color)
        style.configure("TLabel", background=bg_color, foreground=fg_color, font=('Helvetica', 12))
        style.configure("TButton", background="#333333", foreground=fg_color, borderwidth=1, font=('Helvetica', 11))
        style.map("TButton", background=[('active', '#444444')])
        
        style.configure("TCombobox", fieldbackground=input_bg, background="#333333", foreground=fg_color, arrowcolor=fg_color)
        style.map("TCombobox", fieldbackground=[('readonly', input_bg)], selectbackground=[('readonly', input_bg)])
        
        style.configure("TEntry", fieldbackground=input_bg, foreground=fg_color, insertcolor=fg_color)
        
        style.configure("Treeview", background="#252526", foreground="#cccccc", fieldbackground="#252526", borderwidth=0, rowheight=30, font=('Helvetica', 11))
        style.configure("Treeview.Heading", background="#333333", foreground=fg_color, relief="flat", font=('Helvetica', 12, 'bold'))
        style.map("Treeview", background=[('selected', select_bg)], foreground=[('selected', 'white')])
        
        style.configure("TRadiobutton", background=bg_color, foreground=fg_color, font=('Helvetica', 11))

    def load_db_connection(self):
        try:
            if not os.path.exists(DB_PATH):
                messagebox.showerror("Erreur", f"Base de données introuvable :\n{DB_PATH}")
                return

            self.conn = sqlite3.connect(DB_PATH)
            self.cursor = self.conn.cursor()
            print(f"Connexion établie : {DB_PATH}")
        except Exception as e:
            messagebox.showerror("Erreur", f"Impossible d'ouvrir la base de données:\n{e}")

    def get_channels_for_sat(self, sat_id):
        if sat_id in self.cache:
            return self.cache[sat_id]

        print(f"Chargement depuis la DB pour SatID {sat_id}...")
        query = """
        SELECT p.id, p.name 
        FROM program_table p
        JOIN satellite_transponder_table tp ON p.tp_id = tp.id
        WHERE tp.sat_id = ?
        ORDER BY p.name
        """
        self.cursor.execute(query, (sat_id,))
        rows = self.cursor.fetchall()
        
        channel_list = []
        for pid, name in rows:
            self.cursor.execute("SELECT fav_group_id FROM fav_prog_table WHERE prog_id=?", (pid,))
            favs = {row[0] for row in self.cursor.fetchall()}
            
            channel_list.append({
                'id': pid,
                'name': name,
                'favs': favs
            })
        
        self.cache[sat_id] = channel_list
        return channel_list

    def setup_ui(self):
        # --- Top Control Panel ---
        top_frame = ttk.Frame(self.root, padding=10)
        top_frame.pack(fill=tk.X)

        # Satellite Selection
        ttk.Label(top_frame, text="Satellite:").pack(side=tk.LEFT, padx=(0, 5))
        self.sat_var = tk.StringVar()
        self.sat_combo = ttk.Combobox(top_frame, textvariable=self.sat_var, values=list(SAT_MAP.keys()), state="readonly", width=15)
        self.sat_combo.pack(side=tk.LEFT, padx=5)
        self.sat_combo.bind("<<ComboboxSelected>>", self.on_sat_change)

        # Filter Input
        ttk.Label(top_frame, text="Filtre:").pack(side=tk.LEFT, padx=(20, 5))
        self.search_var = tk.StringVar()
        self.search_entry = ttk.Entry(top_frame, textvariable=self.search_var, width=20)
        self.search_entry.pack(side=tk.LEFT, padx=5)
        self.search_entry.bind("<KeyRelease>", self.on_search)

        # Export Button
        save_btn = ttk.Button(top_frame, text="Exporter DB", command=self.save_new_db)
        save_btn.pack(side=tk.RIGHT, padx=5)

        # --- Treeview (List) ---
        tree_frame = ttk.Frame(self.root)
        tree_frame.pack(fill=tk.BOTH, expand=True, padx=10, pady=5)

        columns = ["name"] + FAV_LABELS
        self.tree = ttk.Treeview(tree_frame, columns=columns, show="headings", selectmode="extended")
        
        vsb = ttk.Scrollbar(tree_frame, orient="vertical", command=self.tree.yview)
        vsb.pack(side=tk.RIGHT, fill=tk.Y)
        self.tree.configure(yscrollcommand=vsb.set)
        self.tree.pack(fill=tk.BOTH, expand=True)

        self.tree.heading("name", text="Chaîne")
        self.tree.column("name", width=300, anchor="w")
        
        for label in FAV_LABELS:
            self.tree.heading(label, text=label)
            self.tree.column(label, width=60, anchor="center")

        # Bindings
        self.tree.bind("<ButtonRelease-1>", self.on_tree_click)

        # --- Bottom Action Panel ---
        bottom_frame = ttk.Frame(self.root, padding=10)
        bottom_frame.pack(fill=tk.X)
        
        fav_btn = ttk.Button(bottom_frame, text="★ Gérer Favoris", command=self.open_fav_dialog)
        fav_btn.pack(side=tk.LEFT, fill=tk.X, expand=True, padx=50)

    def on_sat_change(self, event):
        sat_name = self.sat_var.get()
        sat_id = SAT_MAP[sat_name]
        self.current_sat_id = sat_id
        
        # Load Data
        self.current_channels = self.get_channels_for_sat(sat_id)
        print(f"Chargé {len(self.current_channels)} chaînes pour {sat_name}")
        
        # Refresh Display
        self.refresh_tree()

    def on_search(self, event):
        self.refresh_tree()

    def refresh_tree(self):
        # Clear Tree
        for item in self.tree.get_children():
            self.tree.delete(item)
        self.tree_item_map.clear()
        self.filtered_indices.clear()

        query = self.search_var.get().lower()
        
        # Filter and Populate
        for idx, ch in enumerate(self.current_channels):
            if query and query not in ch['name'].lower():
                continue
                
            self.filtered_indices.append(idx)
            
            # Prepare values
            values = [ch['name']]
            for label in FAV_LABELS:
                fav_id = FAV_MAP[label]
                values.append(CHECKED if fav_id in ch['favs'] else UNCHECKED)
            
            item_id = self.tree.insert("", "end", values=values)
            self.tree_item_map[item_id] = idx

    def on_tree_click(self, event):
        # Handle clicks on checkboxes directly
        region = self.tree.identify("region", event.x, event.y)
        if region != "cell":
            return

        column = self.tree.identify_column(event.x)
        item_id = self.tree.identify_row(event.y)
        
        if not item_id or item_id not in self.tree_item_map:
            return

        col_idx = int(column.replace('#', '')) - 1
        
        if col_idx == 0:
            return # Clicked on name

        fav_label = FAV_LABELS[col_idx - 1]
        fav_id = FAV_MAP[fav_label]
        
        ch_idx = self.tree_item_map[item_id]
        ch_data = self.current_channels[ch_idx]
        
        # Toggle
        if fav_id in ch_data['favs']:
            ch_data['favs'].remove(fav_id)
            new_val = UNCHECKED
        else:
            ch_data['favs'].add(fav_id)
            new_val = CHECKED
            
        self.tree.set(item_id, column=fav_label, value=new_val)

    def open_fav_dialog(self):
        selected_items = self.tree.selection()
        if not selected_items:
            messagebox.showwarning("Attention", "Veuillez sélectionner au moins une chaîne.")
            return

        # Create Dialog
        dialog = tk.Toplevel(self.root)
        dialog.title("Gestion Favoris")
        dialog.geometry("400x350")
        dialog.configure(bg="#1e1e1e")
        dialog.transient(self.root)
        dialog.grab_set()
        
        # Center dialog
        x = self.root.winfo_x() + (self.root.winfo_width() // 2) - 200
        y = self.root.winfo_y() + (self.root.winfo_height() // 2) - 175
        dialog.geometry(f"+{x}+{y}")

        ttk.Label(dialog, text=f"{len(selected_items)} chaînes sélectionnées", font=('Helvetica', 12, 'bold')).pack(pady=10)
        
        # Radio Buttons for Group Selection
        self.selected_fav_group = tk.StringVar(value=FAV_LABELS[0])
        
        frame_radios = ttk.Frame(dialog)
        frame_radios.pack(pady=10, padx=20, fill=tk.BOTH, expand=True)
        
        for label in FAV_LABELS:
            ttk.Radiobutton(frame_radios, text=label, variable=self.selected_fav_group, value=label).pack(anchor=tk.W, pady=2)

        # Action Buttons
        frame_btns = ttk.Frame(dialog)
        frame_btns.pack(pady=20, fill=tk.X)
        
        def apply_fav(action):
            group_label = self.selected_fav_group.get()
            fav_id = FAV_MAP[group_label]
            
            count = 0
            for item_id in selected_items:
                ch_idx = self.tree_item_map[item_id]
                ch_data = self.current_channels[ch_idx]
                
                if action == "add":
                    if fav_id not in ch_data['favs']:
                        ch_data['favs'].add(fav_id)
                        self.tree.set(item_id, column=group_label, value=CHECKED)
                        count += 1
                else:
                    if fav_id in ch_data['favs']:
                        ch_data['favs'].remove(fav_id)
                        self.tree.set(item_id, column=group_label, value=UNCHECKED)
                        count += 1
            
            # messagebox.showinfo("Info", f"{count} chaînes mises à jour ({action}).")
            dialog.destroy()

        ttk.Button(frame_btns, text="Ajouter au groupe", command=lambda: apply_fav("add")).pack(side=tk.LEFT, padx=10, expand=True)
        ttk.Button(frame_btns, text="Retirer du groupe", command=lambda: apply_fav("remove")).pack(side=tk.LEFT, padx=10, expand=True)

    def save_new_db(self):
        try:
            print(f"Création de {NEW_DB_PATH}...")
            shutil.copy2(DB_PATH, NEW_DB_PATH)
            
            new_conn = sqlite3.connect(NEW_DB_PATH)
            new_cursor = new_conn.cursor()
            
            # Update Group Names
            for label, fav_id in FAV_MAP.items():
                new_cursor.execute("UPDATE fav_name_table SET fav_name=? WHERE id=?", (label, fav_id))
            
            # Apply Changes
            # We iterate over ALL cached satellites to ensure all changes are saved
            for sat_id, channel_list in self.cache.items():
                print(f"  Sauvegarde Satellite ID {sat_id}...")
                for ch in channel_list:
                    pid = ch['id']
                    
                    # Clean existing for managed groups
                    for fav_id in FAV_MAP.values():
                        new_cursor.execute("DELETE FROM fav_prog_table WHERE prog_id=? AND fav_group_id=?", (pid, fav_id))
                    
                    # Insert new
                    for fav_id in ch['favs']:
                        if fav_id in FAV_MAP.values():
                            new_cursor.execute("INSERT INTO fav_prog_table (prog_id, fav_group_id, disp_order, tv_type) VALUES (?, ?, ?, ?)", 
                                               (pid, fav_id, 0, 0))
            
            new_conn.commit()
            new_conn.close()
            
            messagebox.showinfo("Succès", f"Export terminé :\n{NEW_DB_PATH}")
            
        except Exception as e:
            messagebox.showerror("Erreur de sauvegarde", str(e))

if __name__ == "__main__":
    root = tk.Tk()
    app = SatEditorApp(root)
    root.mainloop()
