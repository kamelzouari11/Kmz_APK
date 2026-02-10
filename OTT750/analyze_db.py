import sqlite3

db_path = '/home/kamel/OTT750/database.db'

def analyze_db():
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()

        # Count Satellites
        cursor.execute("SELECT COUNT(*) FROM satellite_table")
        sat_count = cursor.fetchone()[0]

        # Count Transponders
        cursor.execute("SELECT COUNT(*) FROM satellite_transponder_table")
        tp_count = cursor.fetchone()[0]

        # Count Programs (Channels)
        cursor.execute("SELECT COUNT(*) FROM program_table")
        prog_count = cursor.fetchone()[0]

        # Count by Service Type (assuming 1=TV, 2=Radio, usually, but let's just group by it)
        cursor.execute("SELECT service_type, COUNT(*) FROM program_table GROUP BY service_type")
        service_types = cursor.fetchall()

        # Get first 10 channels
        cursor.execute("SELECT name, service_type FROM program_table LIMIT 10")
        sample_channels = cursor.fetchall()

        print(f"--- Analyse de {db_path} ---")
        print(f"Nombre de satellites: {sat_count}")
        print(f"Nombre de transpondeurs: {tp_count}")
        print(f"Nombre total de chaînes (programmes): {prog_count}")
        
        print("\nRépartition par type de service (service_type):")
        for stype, count in service_types:
            print(f"  Type {stype}: {count}")

        print("\nExemple de 10 premières chaînes:")
        for name, stype in sample_channels:
            print(f"  - {name} (Type: {stype})")

        conn.close()

    except Exception as e:
        print(f"Erreur lors de l'analyse: {e}")

if __name__ == "__main__":
    analyze_db()
