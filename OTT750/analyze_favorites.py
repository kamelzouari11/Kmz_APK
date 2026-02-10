import sqlite3

db_path = '/home/kamel/OTT750/database.db'

def analyze_favorites():
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()

        print("--- Groupes de Favoris ---")
        cursor.execute("SELECT id, fav_name FROM fav_name_table")
        fav_groups = cursor.fetchall()
        
        for grp_id, grp_name in fav_groups:
            # Count channels in this group
            cursor.execute("SELECT COUNT(*) FROM fav_prog_table WHERE fav_group_id=?", (grp_id,))
            count = cursor.fetchone()[0]
            print(f"ID: {grp_id} | Nom: {grp_name} | Nombre de chaînes: {count}")

        print("\n--- Détail du groupe 'News' (ID 3) ---")
        # Let's list channels for group ID 3 as an example
        query = """
        SELECT p.name, fp.disp_order 
        FROM fav_prog_table fp
        JOIN program_table p ON fp.prog_id = p.id
        WHERE fp.fav_group_id = 3
        ORDER BY fp.disp_order
        LIMIT 10
        """
        cursor.execute(query)
        channels = cursor.fetchall()
        
        if channels:
            for name, order in channels:
                print(f"  {order}. {name}")
        else:
            print("  Aucune chaîne dans ce groupe.")

        conn.close()

    except Exception as e:
        print(f"Erreur: {e}")

if __name__ == "__main__":
    analyze_favorites()
