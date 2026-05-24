#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Complete GUI for editing OTT750 channel favorites (Tkinter)

import sqlite3
import tkinter as tk
from tkinter import ttk, messagebox
import shutil
import os

DB_FILE = "database.db"
OUTPUT_DB = "database_new.db"

FAVORITE_LISTS = ["sport", "news", "cinema", "france", "italie", "nilesat"]

class ChannelEditorApp:
    def __init__(self, root):
        self.root = root
        root.title("OTT750 - Channel Favorites Editor")
        root.geometry("1100x700")

        self.channels = {}
        self.check_vars = {}

        self.build_ui()
        self.load_channels()

    def build_ui(self):
        top = tk.Frame(self.root)
        top.pack(fill="x", pady=5)

        tk.Label(top, text="Search:").pack(side="left")
        self.search_var = tk.StringVar()
        self.search_var.trace_add("write", self.refresh_tree)
        tk.Entry(top, textvariable=self.search_var, width=30).pack(side="left", padx=5)

        tk.Button(top, text="Save", command=self.save_changes, bg="lightgreen").pack(side="right", padx=5)

        columns = ("lcn", "name", "sat")
        self.tree = ttk.Treeview(self.root, columns=columns, show="headings")
        self.tree.heading("lcn", text="LCN")
        self.tree.heading("name", text="Channel Name")
        self.tree.heading("sat", text="Satellite")
        self.tree.pack(fill="both", expand=True)

        bottom = tk.Frame(self.root)
        bottom.pack(fill="x", pady=5)

        self.favorite_vars = {}
        for fav in FAVORITE_LISTS:
            var = tk.BooleanVar()
            self.favorite_vars[fav] = var
            tk.Checkbutton(bottom, text=fav.capitalize(), variable=var).pack(side="left", padx=5)

    def load_channels(self):
        if not os.path.exists(DB_FILE):
            messagebox.showerror("Error", f"Database not found: {DB_FILE}")
            return

        conn = sqlite3.connect(DB_FILE)
        c = conn.cursor()

        try:
            # Remplacer "satellite" par le nom exact de la colonne dans ta DB si différent
            c.execute("SELECT id, lcn_no, name, satellite, fav FROM program_table ORDER BY satellite, lcn_no")
        except Exception as e:
            conn.close()
            tk.Tk().withdraw()  # permet d'afficher messagebox même si root a des problèmes
            messagebox.showerror("DB Error", str(e))
            return

        rows = c.fetchall()
        conn.close()

        self.channels.clear()

        for row in rows:
            ch_id, lcn, name, sat, fav = row
            try:
                fav = int(fav)
            except:
                fav = 0

            if sat not in self.channels:
                self.channels[sat] = []

            self.channels[sat].append({
                "id": ch_id,
                "lcn": lcn,
                "name": name,
                "sat": sat,
                "fav": fav,
                "favorites": set()
            })

        self.refresh_tree()

    def refresh_tree(self, *args):
        query = self.search_var.get().lower()
        for item in self.tree.get_children():
            self.tree.delete(item)

        for sat in sorted(self.channels.keys()):
            for ch in self.channels[sat]:
                if query in ch["name"].lower():
                    self.tree.insert("", "end", iid=f"{ch['id']}", values=(ch["lcn"], ch["name"], ch["sat"]))

    def save_changes(self):
        if not os.path.exists(DB_FILE):
            messagebox.showerror("Error", "database.db not found")
            return

        shutil.copyfile(DB_FILE, OUTPUT_DB)
        conn = sqlite3.connect(OUTPUT_DB)
        c = conn.cursor()

        try:
            c.execute("UPDATE program_table SET fav = 0")
        except:
            pass

        # Liste des favoris sélectionnés via Checkbuttons
        selected_favs = [fav for fav, var in self.favorite_vars.items() if var.get()]

        if not selected_favs:
            messagebox.showinfo("Info", "No favorite list selected.")

        # Appliquer aux chaînes sélectionnées dans le Treeview
        for item in self.tree.selection():
            ch_id = int(item)
            c.execute("UPDATE program_table SET fav = 1 WHERE id = ?", (ch_id,))

        conn.commit()
        conn.close()

        messagebox.showinfo("Done", f"Saved to {OUTPUT_DB}\nCopy to USB and restore on OTT750 receiver.")

if __name__ == "__main__":
    root = tk.Tk()
    app = ChannelEditorApp(root)
    root.mainloop()
