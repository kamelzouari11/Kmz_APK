import sqlite3

db_path = '/home/kamel/OTT750/database.db'

def analyze_favorites_detailed():
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()

        # Get all groups
        cursor.execute("SELECT id, fav_name FROM fav_name_table")
        fav_groups = cursor.fetchall()
        
        print("--- Contenu Détaillé des Favoris ---")

        for grp_id, grp_name in fav_groups:
            # Get channels for this group
            query = """
            SELECT p.name, fp.disp_order 
            FROM fav_prog_table fp
            JOIN program_table p ON fp.prog_id = p.id
            WHERE fp.fav_group_id = ?
            ORDER BY fp.disp_order
            """
            cursor.execute(query, (grp_id,))
            channels = cursor.fetchall()

            # Only print if the group is not empty
            if channels:
                print(f"\nGroupe : {grp_name} (ID: {grp_id}) - {len(channels)} chaînes")
                for name, order in channels:
                    print(f"  {order}. {name}")

        conn.close()

    except Exception as e:
        print(f"Erreur: {e}")

if __name__ == "__main__":
    analyze_favorites_detailed()
