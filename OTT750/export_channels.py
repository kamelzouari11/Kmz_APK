import sqlite3
import csv
import os

db_path = '/home/kamel/OTT750/database.db'
csv_path = '/home/kamel/OTT750/liste_chaines.csv'

def export_to_csv():
    try:
        conn = sqlite3.connect(db_path)
        cursor = conn.cursor()

        query = """
        SELECT 
            p.name as Channel_Name,
            s.name as Satellite_Name,
            tp.freq as Frequency,
            tp.pol as Polarization,
            tp.sym_rate as Symbol_Rate,
            p.service_type as Service_Type,
            p.vid_type as Video_Type
        FROM program_table p
        LEFT JOIN satellite_transponder_table tp ON p.tp_id = tp.id
        LEFT JOIN satellite_table s ON tp.sat_id = s.id
        ORDER BY s.name, p.name
        """

        cursor.execute(query)
        rows = cursor.fetchall()

        # Define column names based on the query
        headers = ["Nom de la chaîne", "Satellite", "Fréquence", "Polarisation", "Symbol Rate", "Type Service", "Type Vidéo"]

        # Map polarization values if possible (Common: 0=H, 1=V)
        # But without certainty, I will keep raw values or add a helper if I knew the mapping.
        # Let's just write the raw data first.

        with open(csv_path, 'w', newline='', encoding='utf-8') as f:
            writer = csv.writer(f)
            writer.writerow(headers)
            
            for row in rows:
                # row is a tuple
                # Clean up frequency (often stored in MHz or kHz, sometimes needs adjustment)
                # Usually stored as integer e.g. 11900
                
                # Clean up Polarization: 0 -> H, 1 -> V is standard for many STBs
                pol_map = {0: 'H', 1: 'V'}
                pol_val = row[3]
                pol_str = pol_map.get(pol_val, str(pol_val))

                new_row = [
                    row[0], # Name
                    row[1], # Sat Name
                    row[2], # Freq
                    pol_str, # Pol
                    row[4], # Sym Rate
                    row[5], # Service Type
                    row[6]  # Video Type
                ]
                writer.writerow(new_row)

        print(f"Export réussi : {csv_path}")
        print(f"Nombre de chaînes exportées : {len(rows)}")

        conn.close()

    except Exception as e:
        print(f"Erreur lors de l'export: {e}")

if __name__ == "__main__":
    export_to_csv()
