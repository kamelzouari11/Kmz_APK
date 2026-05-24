#!/usr/bin/env python3
"""
Script pour enrichir la base de données OTT750 avec les informations de provider
en utilisant la table tp_network_name_table et le fichier provider_mapping.csv
"""

import sqlite3
import csv
import os

# Chemins des fichiers
DB_PATH = '/home/kamel/OTT750/database.db'
DB_OUTPUT = '/home/kamel/OTT750/database_enriched.db'
MAPPING_CSV = '/home/kamel/OTT750/provider_mapping.csv'

def load_provider_mapping():
    """Charge le fichier CSV de mapping provider"""
    mappings = []
    
    if not os.path.exists(MAPPING_CSV):
        print(f"⚠️ Fichier {MAPPING_CSV} non trouvé")
        return mappings
    
    with open(MAPPING_CSV, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            mappings.append({
                'satellite': row['satellite'],
                'position': row['position'],
                'freq_min': int(row['freq_min']),
                'freq_max': int(row['freq_max']),
                'pol': row.get('pol', ''),
                'provider': row['provider'],
                'package': row.get('package', '')
            })
    
    print(f"✅ Chargé {len(mappings)} mappings provider")
    return mappings


def get_provider_for_freq(mappings, satellite, freq):
    """Trouve le provider pour une fréquence donnée"""
    for m in mappings:
        if m['satellite'].lower() in satellite.lower():
            if m['freq_min'] <= freq <= m['freq_max']:
                return m['provider']
    return None


def enrich_database():
    """Enrichit la base de données avec les providers"""
    
    mappings = load_provider_mapping()
    
    # Copier la base de données
    import shutil
    shutil.copy2(DB_PATH, DB_OUTPUT)
    print(f"📋 Base copiée vers {DB_OUTPUT}")
    
    conn = sqlite3.connect(DB_OUTPUT)
    cursor = conn.cursor()
    
    # 1. D'abord, utiliser tp_network_name_table pour enrichir
    print("\n📊 Analyse de tp_network_name_table...")
    cursor.execute("SELECT DISTINCT name FROM tp_network_name_table WHERE name != ''")
    network_names = [row[0] for row in cursor.fetchall()]
    print(f"   Réseaux trouvés: {network_names[:20]}...")
    
    # 2. Créer une vue avec les providers
    print("\n🔧 Création de la vue enrichie...")
    
    # Requête pour joindre les tables et obtenir les infos complètes
    query = """
    SELECT 
        p.id,
        p.name as channel_name,
        p.tp_id,
        t.freq,
        t.pol,
        s.name as satellite_name,
        n.name as network_name,
        p.provider_id
    FROM program_table p
    LEFT JOIN satellite_transponder_table t ON p.tp_id = t.id
    LEFT JOIN satellite_table s ON t.sat_id = s.id
    LEFT JOIN tp_network_name_table n ON p.network_name_id = n.id
    WHERE p.name != ''
    ORDER BY p.id
    """
    
    cursor.execute(query)
    channels = cursor.fetchall()
    print(f"   {len(channels)} chaînes trouvées")
    
    # 3. Créer un fichier CSV avec les chaînes enrichies
    output_csv = '/home/kamel/OTT750/channels_with_providers.csv'
    
    stats = {'total': 0, 'from_network': 0, 'from_mapping': 0, 'unknown': 0}
    
    with open(output_csv, 'w', newline='', encoding='utf-8') as f:
        writer = csv.writer(f)
        writer.writerow(['id', 'channel_name', 'freq', 'pol', 'satellite', 'network_name', 'provider_mapped', 'provider_final'])
        
        for row in channels:
            ch_id, ch_name, tp_id, freq, pol, sat_name, network_name, provider_id = row
            stats['total'] += 1
            
            # Déterminer le provider final
            provider_final = ''
            
            # 1. Essayer network_name de la DB
            if network_name and network_name.strip():
                provider_final = network_name.strip()
                stats['from_network'] += 1
            
            # 2. Sinon, utiliser le mapping par fréquence
            elif freq and sat_name:
                mapped = get_provider_for_freq(mappings, sat_name, freq)
                if mapped:
                    provider_final = mapped
                    stats['from_mapping'] += 1
                else:
                    provider_final = 'Unknown'
                    stats['unknown'] += 1
            else:
                provider_final = 'Unknown'
                stats['unknown'] += 1
            
            writer.writerow([ch_id, ch_name, freq, pol, sat_name, network_name or '', 
                           get_provider_for_freq(mappings, sat_name or '', freq or 0) or '', 
                           provider_final])
    
    print(f"\n💾 Exporté vers {output_csv}")
    
    # 4. Statistiques
    print("\n📈 Statistiques:")
    print(f"   Total chaînes: {stats['total']}")
    print(f"   Provider depuis network_name: {stats['from_network']} ({100*stats['from_network']/stats['total']:.1f}%)")
    print(f"   Provider depuis mapping CSV: {stats['from_mapping']} ({100*stats['from_mapping']/stats['total']:.1f}%)")
    print(f"   Provider inconnu: {stats['unknown']} ({100*stats['unknown']/stats['total']:.1f}%)")
    
    # 5. Afficher les providers les plus fréquents
    print("\n📋 Top providers (depuis network_name):")
    cursor.execute("""
        SELECT n.name, COUNT(*) as cnt 
        FROM program_table p 
        JOIN tp_network_name_table n ON p.network_name_id = n.id 
        WHERE n.name != '' 
        GROUP BY n.name 
        ORDER BY cnt DESC 
        LIMIT 15
    """)
    for name, count in cursor.fetchall():
        print(f"   {name}: {count} chaînes")
    
    conn.close()
    print("\n✅ Terminé!")


if __name__ == '__main__':
    enrich_database()
