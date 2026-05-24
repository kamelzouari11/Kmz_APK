import socket
import sys
import re
from urllib.parse import urlparse

def get_metadata(url):
    try:
        parsed = urlparse(url)
        host = parsed.hostname
        port = parsed.port or 80
        path = parsed.path
        if not path: path = "/"
        
        sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        sock.settimeout(5)
        sock.connect((host, port))
        
        request = f"GET {path} HTTP/1.0\r\nHost: {host}\r\nIcy-MetaData: 1\r\nUser-Agent: Winamp/2.8\r\n\r\n"
        sock.sendall(request.encode('utf-8'))
        
        response = b""
        
        # Read headers
        while b"\r\n\r\n" not in response:
            chunk = sock.recv(1024)
            if not chunk: break
            response += chunk
            
        parts = response.split(b"\r\n\r\n", 1)
        if len(parts) < 2:
            return
            
        headers, data = parts
        headers_str = headers.decode('utf-8', errors='ignore')
        
        metaint = -1
        # Find metaint
        for line in headers_str.split('\r\n'):
            if line.lower().startswith('icy-metaint'):
                metaint = int(line.split(':')[1])
                break
                
        if metaint == -1:
            print(f"[{url}] -> PAS DE METAINT (Pas de support ICY ou HLS/DASH)")
            return

        # Read until we have enough data for the first metadata block
        while len(data) < metaint + 255: 
            chunk = sock.recv(1024)
            if not chunk: break
            data += chunk
            
        if len(data) > metaint:
            # The byte at 'metaint' index is the length of the metadata / 16
            try:
                meta_len_byte = data[metaint]
                meta_len = meta_len_byte * 16
                if meta_len > 0:
                    metadata = data[metaint+1 : metaint+1+meta_len]
                    print(f"[{url}] -> {metadata.decode('utf-8', errors='ignore')}")
                else:
                    print(f"[{url}] -> METADATA VIDE (StreamTitle='')")
            except IndexError:
                print(f"[{url}] -> Erreur d'index")
        
        sock.close()
    except Exception as e:
        print(f"[{url}] -> Erreur: {e}")

urls = [
    "http://icecast.radiofrance.fr/fip-midfi.mp3",
    "http://dancewave.online/dance.mp3",
    "http://uk3.internet-radio.com:8405/live", 
    "http://stream.radioparadise.com/mp3-128",
    "http://direct.franceinter.fr/live/franceinter-midfi.mp3"
]

print("--- EXEMPLES BRUTS DE METADONNEES ---")
for u in urls:
    get_metadata(u)
