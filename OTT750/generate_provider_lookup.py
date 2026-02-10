#!/usr/bin/env python3
"""
Génère un fichier JSON de lookup channel_name -> provider
pour l'app Android
"""

import json
import csv

INPUT_CSV = '/home/kamel/OTT750/channels_with_providers.csv'
OUTPUT_JSON = '/home/kamel/OTT750/OTT750_Android/app/src/main/assets/channel_providers.json'

def main():
    lookup = {}
    
    with open(INPUT_CSV, 'r', encoding='utf-8') as f:
        reader = csv.DictReader(f)
        for row in reader:
            name = row['channel_name'].strip()
            provider = row['provider_final'].strip()
            
            # Ne pas ajouter les chaînes sans nom ou provider inconnu
            if name and name != 'Unname' and provider and provider != 'Unknown':
                # Normaliser le nom (minuscules, sans espaces multiples)
                normalized_name = ' '.join(name.lower().split())
                lookup[normalized_name] = provider
    
    # Sauvegarder en JSON
    with open(OUTPUT_JSON, 'w', encoding='utf-8') as f:
        json.dump(lookup, f, ensure_ascii=False, indent=2)
    
    print(f"✅ Généré {OUTPUT_JSON}")
    print(f"   {len(lookup)} chaînes avec provider")

if __name__ == '__main__':
    main()
