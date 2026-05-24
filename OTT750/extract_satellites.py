#!/usr/bin/env python3
"""
Script pour créer satellites_select.xml avec seulement les satellites sélectionnés:
- Astra 19E
- Hotbird 13E
- Nilesat 7W et 8W
"""

import xml.etree.ElementTree as ET
import re

INPUT_FILES = [
    '/home/kamel/OTT750/satellites_1.xml',
    '/home/kamel/OTT750/satellites_2.xml'
]
OUTPUT_FILE = '/home/kamel/OTT750/satellites_select.xml'

# Positions des satellites à inclure (en dixièmes de degré)
# Positif = Est, Négatif = Ouest
SELECTED_POSITIONS = [
    190,   # Astra 1 19E Ku
    191,   # Astra 1 19E Ka (optionnel)
    130,   # Hotbird 13E
    -70,   # Nilesat 7W
    -71,   # Nilesat 7W Ka (optionnel)
    -80,   # Eutelsat 8 West B (8W)
    -81,   # Eutelsat 8 West (8W) C
]


def is_selected_satellite(sat_elem):
    """Vérifie si un satellite doit être inclus"""
    name = sat_elem.get('name', '').lower()
    position = sat_elem.get('position', '')
    
    # Sélection stricte par position uniquement
    try:
        pos = int(position)
        if pos in SELECTED_POSITIONS:
            return True
    except:
        pass
    
    return False


def main():
    print("=" * 60)
    print("📡 Création de satellites_select.xml")
    print("=" * 60)
    
    selected_satellites = []
    
    for filepath in INPUT_FILES:
        print(f"\n📁 Lecture de {filepath}...")
        
        try:
            tree = ET.parse(filepath)
            root = tree.getroot()
            
            for sat in root.findall('sat'):
                name = sat.get('name', '')
                position = sat.get('position', '')
                
                if is_selected_satellite(sat):
                    transponder_count = len(sat.findall('transponder'))
                    print(f"  ✅ {name} (position={position}) - {transponder_count} transponders")
                    selected_satellites.append(sat)
                    
        except Exception as e:
            print(f"  ❌ Erreur: {e}")
    
    print(f"\n📊 Total: {len(selected_satellites)} satellites sélectionnés")
    
    # Créer le nouveau fichier XML
    new_root = ET.Element('satellites')
    new_root.text = '\n  '
    
    for i, sat in enumerate(selected_satellites):
        # Ajouter le satellite
        new_root.append(sat)
        
        # Formatage
        sat.tail = '\n  ' if i < len(selected_satellites) - 1 else '\n'
    
    # Écrire le fichier
    tree = ET.ElementTree(new_root)
    
    # Ajouter l'en-tête XML
    with open(OUTPUT_FILE, 'wb') as f:
        f.write(b'<?xml version="1.0" encoding="utf-8"?>\n')
        f.write(b'<!--Selected satellites: Astra 19E, Hotbird 13E, Nilesat 7W/8W-->\n')
        tree.write(f, encoding='utf-8', xml_declaration=False)
    
    print(f"\n💾 Sauvegardé: {OUTPUT_FILE}")
    
    # Stats
    total_transponders = sum(len(sat.findall('transponder')) for sat in selected_satellites)
    print(f"📡 Transponders totaux: {total_transponders}")


if __name__ == '__main__':
    main()
