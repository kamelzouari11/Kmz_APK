#!/usr/bin/env python3
"""
Script pour enrichir database.db avec les providers depuis satellites_select.xml
Matching par: Satellite (angle/position) + Fréquence
"""

import sqlite3
import xml.etree.ElementTree as ET
import os
import shutil

DB_PATH = '/home/kamel/OTT750/database.db'
DB_OUTPUT = '/home/kamel/OTT750/database_enriched.db'
SATELLITES_XML = '/home/kamel/OTT750/satellites_select.xml'

# Mapping des angles database.db vers positions satellites_select.xml
# database.db: angle positif = Est (ex: 192 = 19.2E)
# database.db: angle négatif ou <0 = Ouest (mais stocké positif avec convention)
# satellites_select.xml: position positive = Est, négative = Ouest
ANGLE_TO_POSITION = {
    192: 190,   # Astra 19.2E -> 19E
    130: 130,   # Hotbird 13E
    70: -70,    # Nilesat 7W (angle 70 mais c'est Ouest)
    80: -80,    # Eutelsat 8W
}


def load_transponders_from_xml(xml_path):
    """Charge les transponders avec leurs providers depuis le XML"""
    transponders = {}  # (position, freq_mhz) -> provider
    
    tree = ET.parse(xml_path)
    root = tree.getroot()
    
    for sat in root.findall('sat'):
        position = int(sat.get('position', 0))
        sat_name = sat.get('name', '')
        
        for tp in sat.findall('transponder'):
            freq_hz = int(tp.get('frequency', 0))
            freq_mhz = freq_hz // 1000  # Convertir Hz -> MHz
            provider = tp.get('provider', '')
            
            if provider and freq_mhz > 0:
                key = (position, freq_mhz)
                if key not in transponders:
                    transponders[key] = provider
    
    return transponders


def find_provider(transponders, angle, freq):
    """Trouve le provider pour un angle et une fréquence donnés"""
    
    # Convertir l'angle en position
    position = ANGLE_TO_POSITION.get(angle)
    if position is None:
        return None
    
    # Chercher avec tolérance ±10 MHz
    for delta in range(0, 11):
        for sign in [0, 1, -1]:
            test_freq = freq + (delta * sign)
            key = (position, test_freq)
            if key in transponders:
                return transponders[key]
    
    return None


def main():
    print("=" * 60)
    print("🛰️  Enrichissement de database.db avec providers")
    print("=" * 60)
    
    # Charger les transponders du XML
    print(f"\n📁 Chargement de {SATELLITES_XML}...")
    transponders = load_transponders_from_xml(SATELLITES_XML)
    print(f"   {len(transponders)} transponders avec provider chargés")
    
    # Copier la base de données
    print(f"\n📁 Copie de {DB_PATH} vers {DB_OUTPUT}...")
    shutil.copy2(DB_PATH, DB_OUTPUT)
    
    # Ouvrir la copie
    conn = sqlite3.connect(DB_OUTPUT)
    cursor = conn.cursor()
    
    # Vérifier si la colonne provider existe, sinon la créer
    cursor.execute("PRAGMA table_info(program_table)")
    columns = [col[1] for col in cursor.fetchall()]
    
    if 'provider' not in columns:
        print("   Ajout de la colonne 'provider'...")
        cursor.execute("ALTER TABLE program_table ADD COLUMN provider VARCHAR(64) DEFAULT ''")
        conn.commit()
    
    # Récupérer toutes les chaînes avec leur satellite et fréquence
    cursor.execute("""
        SELECT p.id, p.name, s.angle, t.freq
        FROM program_table p
        JOIN satellite_transponder_table t ON p.tp_id = t.id
        JOIN satellite_table s ON t.sat_id = s.id
        WHERE p.name != '' AND p.name != 'Unname'
    """)
    
    channels = cursor.fetchall()
    print(f"\n📺 {len(channels)} chaînes à traiter")
    
    # Enrichir chaque chaîne
    stats = {'found': 0, 'not_found': 0}
    updates = []
    
    for channel_id, name, angle, freq in channels:
        provider = find_provider(transponders, angle, freq)
        
        if not provider:
            provider = 'Other'
            stats['not_found'] += 1
        else:
            stats['found'] += 1
            
        updates.append((provider, channel_id))
    
    # Appliquer les mises à jour
    print(f"\n💾 Application des mises à jour...")
    cursor.executemany("UPDATE program_table SET provider = ? WHERE id = ?", updates)
    conn.commit()
    
    # Stats par provider
    cursor.execute("""
        SELECT provider, COUNT(*) as cnt 
        FROM program_table 
        WHERE provider != '' 
        GROUP BY provider 
        ORDER BY cnt DESC 
        LIMIT 20
    """)
    
    print(f"\n📊 Résultats:")
    print(f"   ✅ Avec provider trouvé: {stats['found']} ({100*stats['found']//len(channels)}%)")
    print(f"   ⚠️  Provider 'Other': {stats['not_found']}")
    
    print(f"\n📋 Top providers:")
    for provider, count in cursor.fetchall():
        print(f"   {provider}: {count}")
    
    # Analyse comparative des fréquences (XML vs DB) pour Astra (192) et Hotbird (130)
    print("\n🔍 Analyse Fréquences (XML vs DB):")
    
    # 1. Fréquences du XML (Astra=190, Hotbird=130)
    xml_freqs = {190: set(), 130: set()}
    for (pos, freq) in transponders.keys():
        if pos in xml_freqs:
            xml_freqs[pos].add(freq)
            
    # 2. Fréquences de la DB (Astra=192, Hotbird=130)
    cursor.execute("""
        SELECT s.angle, t.freq 
        FROM satellite_transponder_table t
        JOIN satellite_table s ON t.sat_id = s.id
        WHERE s.angle IN (192, 130)
    """)
    db_freqs = {192: set(), 130: set()}
    for angle, freq in cursor.fetchall():
        if angle in db_freqs:
            db_freqs[angle].add(freq)
            
    # Rapport
    for db_angle, xml_pos in [(192, 190), (130, 130)]:
        sat_name = "Astra" if db_angle == 192 else "Hotbird"
        d_freq = db_freqs[db_angle]
        x_freq = xml_freqs[xml_pos]
        
        # Trouver les correspondances avec tolérance +/- 10
        matched = 0
        missed = []
        for df in d_freq:
            found = False
            for delta in range(0, 11):
                if (df + delta in x_freq) or (df - delta in x_freq):
                    found = True
                    break
            if found:
                matched += 1
            else:
                missed.append(df)
        
        print(f"\n   🌍 {sat_name}:")
        print(f"      XML Transponders: {len(x_freq)}")
        print(f"      DB Transponders : {len(d_freq)}")
        print(f"      CORRESPONDANCES : {matched} ({(matched/len(d_freq)*100) if d_freq else 0:.1f}%)")
        print(f"      MANQUANTS (DB mais pas dans XML): {len(missed)}")
        if missed:
            print(f"      Exemples manquants: {sorted(list(missed))[:10]}...")

    conn.close()
    
    print(f"\n💾 Base enrichie sauvegardée: {DB_OUTPUT}")
    print(f"📱 Copier ce fichier dans Downloads du téléphone")


if __name__ == '__main__':
    main()
