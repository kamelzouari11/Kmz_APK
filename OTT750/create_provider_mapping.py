#!/usr/bin/env python3
"""
Script pour créer un mapping channel -> provider 
en utilisant les données de la database.db et les infos de packages par fréquence
"""

import sqlite3
import json
import re

DB_PATH = '/home/kamel/OTT750/database.db'
OUTPUT_JSON = '/home/kamel/OTT750/OTT750_Android/app/src/main/assets/channel_providers.json'

# Mapping fréquence -> provider basé sur la connaissance des bouquets
# Sources: KingOfSat, expertise du domaine
ASTRA_FREQ_PROVIDER = {
    # Movistar+ (Espagne) - 10714-10936 MHz principalement
    10714: 'Movistar+',
    10729: 'Movistar+', 
    10744: 'Movistar+',
    10758: 'Movistar+',
    10773: 'German FTA',  # QVC, WELT, etc
    10788: 'Movistar+',
    10803: 'Movistar+',
    10817: 'Movistar+',
    10832: 'Canal+',  # Canal+ France
    10847: 'Canal+',
    10861: 'Canal+',
    10876: 'Canal+',
    10891: 'Canal+',
    10906: 'Canal+',
    10920: 'German FTA',
    10936: 'German FTA',
    10964: 'HD+',
    10979: 'HD+',
    10994: 'HD+',
    11008: 'HD+',
    11023: 'Sky Deutschland',
    11038: 'Sky Deutschland',
    11052: 'Sky Deutschland',
    11067: 'Sky Deutschland',
    11082: 'Sky Deutschland',
    11097: 'Movistar+',
    11111: 'Sky Deutschland',
    11126: 'Sky Deutschland',
    11141: 'Sky Deutschland',
    11156: 'Sky Deutschland',
    11170: 'Sky Deutschland',
    11185: 'Sky Deutschland',
    11229: 'Sky Deutschland',
    11244: 'Sky Deutschland',
    11258: 'Sky Deutschland',
    11273: 'Sky Deutschland',
    11288: 'Sky Deutschland',
    11302: 'Sky Deutschland',
    11317: 'German FTA',
    11347: 'Sky Deutschland',
    11362: 'Sky Deutschland',
    11376: 'Sky Deutschland',
    11391: 'German FTA',
    11420: 'German FTA',
    11435: 'German FTA',
    11464: 'German FTA',
    11493: 'German FTA',
    11508: 'German FTA',
    11523: 'German FTA',
    11538: 'German FTA',
    11552: 'German FTA',
    11582: 'German FTA',
    11626: 'German FTA',
    11641: 'German FTA',
    11670: 'German FTA',
    11739: 'German FTA',  # ARD/ZDF
    11758: 'German FTA',
    11778: 'German FTA',
    11797: 'German FTA',
    11817: 'German FTA',
    11836: 'German FTA',
    11856: 'German FTA',
    11875: 'German FTA',
    11895: 'German FTA',
    11914: 'German FTA',
    11934: 'German FTA',
    11953: 'German FTA',
    11973: 'German FTA',
    11992: 'German FTA',
    12012: 'German FTA',
    12031: 'German FTA',
    12051: 'German FTA',
    12070: 'HD+',
    12109: 'HD+',
    12148: 'HD+',
    12187: 'HD+',
    12226: 'HD+',
    12265: 'HD+',
    12304: 'HD+',
    12343: 'HD+',
    12382: 'HD+',
    12421: 'HD+',
    12460: 'HD+',
}

HOTBIRD_FREQ_PROVIDER = {
    # Sky Italia principalement 10719-10853 MHz
    10719: 'Sky Italia',
    10727: 'Sky Italia',
    10758: 'Sky Italia',
    10775: 'Sky Italia',
    10796: 'Sky Italia',
    10814: 'Sky Italia',
    10853: 'Sky Italia',
    10873: 'NC+',  # Polsat/Cyfra+
    10892: 'NC+',
    10911: 'NC+',
    10930: 'NC+',
    10949: 'NC+',
    10971: 'Tivusat',
    10992: 'Tivusat',
    11013: 'Tivusat',
    11034: 'beIN Sports',
    11054: 'beIN Sports',
    11075: 'beIN Sports',
    11096: 'beIN Sports',
    11117: 'Nova',
    11137: 'Nova',
    11158: 'Nova',
    11178: 'Globecast',
    11200: 'Rai',
    11219: 'Rai',
    11240: 'Tivusat',
    11261: 'Tivusat',
    11283: 'Digiturk',
    11304: 'Digiturk',
    11325: 'NC+',
    11355: 'NC+',
    11373: 'NC+',
    11393: 'NC+',
    11411: 'Al Jazeera',
    11432: 'Al Jazeera',
    11470: 'Al Jazeera',
    11508: 'Euronews',
    11526: 'Tivusat',
    11566: 'Tivusat',
    11604: 'Tivusat',
    11642: 'Tivusat',
    11681: 'France TV',
    11727: 'France TV',
    11766: 'France TV',
    11804: 'France TV',
    11843: 'France TV',
    11881: 'France TV',
    11919: 'France TV',
    11958: 'France TV',
    12015: 'beIN Sports',
    12034: 'beIN Sports',
    12054: 'beIN Sports',
    12073: 'beIN Sports',
    12092: 'beIN Sports',
    12111: 'Rai',
    12130: 'Rai',
    12149: 'Rai',
    12169: 'Rai',
    12207: 'Sky Italia',
    12245: 'Sky Italia',
    12284: 'Sky Italia',
    12322: 'Sky Italia',
    12360: 'Sky Italia',
    12399: 'Sky Italia',
    12437: 'Sky Italia',
    12476: 'Sky Italia',
    12520: 'Sky Italia',
    12558: 'Sky Italia',
    12597: 'Sky Italia',
    12635: 'Sky Italia',
    12673: 'Sky Italia',
    12713: 'Sky Italia',
}

NILESAT_FREQ_PROVIDER = {
    # Nilesat 7W - principalement chaînes arabes
    10719: 'Nilesat',
    10758: 'Nilesat',
    10796: 'Nilesat',
    10815: 'MBC',
    10853: 'MBC',
    10892: 'MBC',
    10930: 'beIN Sports MENA',
    10971: 'beIN Sports MENA',
    11013: 'beIN Sports MENA',
    11054: 'OSN',
    11096: 'OSN',
    11137: 'OSN',
    11176: 'ART',
    11219: 'ART',
    11258: 'Al Jazeera',
    11296: 'LBC',
    11334: 'Rotana',
    11373: 'Rotana',
    11411: 'Rotana',
    11449: 'CBC',
    11488: 'CBC',
    11526: 'DMC',
    11564: 'DMC',
    11603: 'Egyptian',
    11641: 'Egyptian',
    11680: 'Egyptian',
    11727: 'Egyptian',
    11766: 'Egyptian',
    11804: 'MBC',
    11843: 'MBC',
    11881: 'MBC',
    11919: 'MBC',
    11958: 'MBC',
    12015: 'Nilesat',
    12054: 'Nilesat',
    12092: 'Nilesat',
    12130: 'Nilesat',
    12169: 'Nilesat',
    12207: 'Nilesat',
    12245: 'Nilesat',
    12284: 'Nilesat',
    12322: 'Nilesat',
    12360: 'Nilesat',
    12399: 'Nilesat',
    12437: 'Nilesat',
    12476: 'Nilesat',
}


def find_closest_freq(freq, freq_map):
    """Trouve la fréquence la plus proche dans le mapping (tolérance ±10MHz)"""
    if freq in freq_map:
        return freq_map[freq]
    
    for f, provider in freq_map.items():
        if abs(f - freq) <= 10:
            return provider
    return None


def get_provider_for_satellite_freq(satellite, freq):
    """Retourne le provider pour un satellite et une fréquence"""
    sat_lower = satellite.lower()
    
    if 'astra' in sat_lower:
        return find_closest_freq(freq, ASTRA_FREQ_PROVIDER)
    elif 'hotbird' in sat_lower or 'hot bird' in sat_lower:
        return find_closest_freq(freq, HOTBIRD_FREQ_PROVIDER)
    elif 'nilesat' in sat_lower or 'nile' in sat_lower:
        return find_closest_freq(freq, NILESAT_FREQ_PROVIDER)
    
    return None


def main():
    print("=" * 60)
    print("🛰️  Création du mapping Channel -> Provider")
    print("=" * 60)
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    # Récupérer toutes les chaînes avec leur fréquence et satellite
    cursor.execute("""
        SELECT p.name, t.freq, s.name as satellite
        FROM program_table p
        JOIN satellite_transponder_table t ON p.tp_id = t.id
        JOIN satellite_table s ON t.sat_id = s.id
        WHERE p.name != '' AND p.name != 'Unname'
    """)
    
    channels = cursor.fetchall()
    print(f"📺 {len(channels)} chaînes trouvées dans la base")
    
    # Créer le mapping
    channel_providers = {}
    stats = {'found': 0, 'not_found': 0}
    
    for name, freq, satellite in channels:
        provider = get_provider_for_satellite_freq(satellite, freq)
        
        if provider:
            # Normaliser le nom
            normalized_name = name.lower().strip()
            normalized_name = re.sub(r'\s+', ' ', normalized_name)
            channel_providers[normalized_name] = provider
            stats['found'] += 1
        else:
            stats['not_found'] += 1
    
    conn.close()
    
    # Sauvegarder en JSON
    with open(OUTPUT_JSON, 'w', encoding='utf-8') as f:
        json.dump(channel_providers, f, ensure_ascii=False, indent=2)
    
    print(f"\n💾 Sauvegardé: {OUTPUT_JSON}")
    print(f"📊 Chaînes avec provider: {stats['found']} ({100*stats['found']//len(channels)}%)")
    print(f"📊 Chaînes sans provider: {stats['not_found']}")
    
    # Stats par provider
    print("\n📋 Répartition par provider:")
    from collections import Counter
    provider_counts = Counter(channel_providers.values())
    for provider, count in provider_counts.most_common(20):
        print(f"   {provider}: {count}")


if __name__ == '__main__':
    main()
