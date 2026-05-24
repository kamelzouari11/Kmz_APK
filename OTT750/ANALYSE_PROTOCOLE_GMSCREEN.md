# Analyse du Protocole de l'APK gmscreen

## 📋 Résumé

L'application **gmscreen** utilise le protocole **UPnP (Universal Plug and Play)** avec une extension propriétaire **Hisilicon MultiScreen** pour communiquer avec le récepteur satellite/IPTV.

---

## 🔧 Protocoles Identifiés

### 1. **UPnP/DLNA (Digital Living Network Alliance)**
- **Discovery** : SSDP (Simple Service Discovery Protocol)
  - Adresse Multicast : `239.255.255.250`
  - Port : `1900`
- **Control** : SOAP sur HTTP
  - Port typique : `49152` (dynamique)

### 2. **Hisilicon MultiScreen Protocol**
C'est une extension propriétaire de Hisilicon pour les chipsets des récepteurs satellite.

#### Services UPnP exposés par le récepteur :
| Service | URN | Description |
|---------|-----|-------------|
| **VinputControlServer** | `urn:schemas-upnp-org:service:VinputControlServer:1` | Contrôle des entrées virtuelles (télécommande) |
| **VIMEControlServer** | `urn:schemas-upnp-org:service:VIMEControlServer:1` | Clavier virtuel / saisie de texte |
| **GsensorControlServer** | `urn:schemas-upnp-org:service:GsensorControlServer:1` | Contrôle par gyroscope (souris air) |
| **AccessControlServer** | `urn:schemas-upnp-org:service:AccessControlServer:1` | Gestion des accès |
| **MirrorControlServer** | `urn:schemas-upnp-org:service:MirrorControlServer:1` | Miroir d'écran |
| **RemoteAppControlServer** | `urn:schemas-upnp-org:service:RemoteAppControlServer:1` | Lancement d'applications à distance |

#### Type de périphérique :
```
urn:schemas-upnp-org:device:HiMultiScreenServerDevice:1
```

---

## 🔄 Flux de Communication

### Phase 1 : Découverte (Discovery)
```
1. Le téléphone envoie un M-SEARCH en multicast sur 239.255.255.250:1900
2. Le récepteur répond avec son adresse IP et le port UPnP
3. Le téléphone récupère le fichier de description : http://<IP>:49152/description.xml
```

### Phase 2 : Contrôle des Chaînes
```
1. Le téléphone se connecte au service VinputControlServer
2. Il envoie des commandes SOAP avec les codes de touches
3. Le récepteur exécute les actions (changement de chaîne, volume, etc.)
```

---

## 📡 Ports Utilisés

| Port | Protocole | Usage |
|------|-----------|-------|
| 1900 | UDP | SSDP Discovery |
| 49152+ | TCP/HTTP | UPnP Control & Events |
| 8888 | TCP | Port de contrôle (cport) |
| 8080 | TCP | Serveur HTTP média |

---

## 🎮 Changement de Chaîne

### Méthode 1 : Envoi de codes de touches (Vinput)
Le téléphone envoie des codes de touches via le service `VinputControlServer` :

```xml
<!-- Exemple de requête SOAP pour envoyer une touche -->
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
  <s:Body>
    <u:SendKeyCode xmlns:u="urn:schemas-upnp-org:service:VinputControlServer:1">
      <KeyValue>XX</KeyValue>
    </u:SendKeyCode>
  </s:Body>
</s:Envelope>
```

### Codes de touches courants (RcuKeyValue)
| Action | Description |
|--------|-------------|
| CH+ | Chaîne suivante |
| CH- | Chaîne précédente |
| 0-9 | Touches numériques |
| OK | Confirmer/Sélectionner |
| MENU | Menu principal |
| EPG | Guide des programmes |
| FAV | Liste des favoris |

### Méthode 2 : Commande directe de chaîne
Via les numéros de chaîne envoyés séquentiellement (touches 0-9).

---

## 🛠️ Classes Java Principales

```
com.hisilicon.multiscreen.controller.VinputUpnpController
com.hisilicon.multiscreen.protocol.remote.RemoteControlCenter
com.hisilicon.multiscreen.protocol.remote.VImeClientController
mktvsmart.screen.GsRemoteControlFragment
```

---

## 🔍 Comment Analyser le Trafic en Temps Réel

### Option 1 : Wireshark
```bash
# Capturer le trafic SSDP et HTTP sur le réseau WiFi
sudo wireshark -i wlan0 -f "port 1900 or port 49152 or port 8888"
```

### Option 2 : tcpdump
```bash
# Capturer les paquets UPnP
sudo tcpdump -i wlan0 -w gmscreen_capture.pcap port 1900 or port 49152
```

### Option 3 : mitmproxy (pour HTTP)
```bash
# Configurer un proxy pour intercepter le trafic HTTP
mitmproxy --mode transparent --showhost
```

---

## 📱 Pour Créer Votre Propre Application

Pour créer une application qui contrôle le récepteur :

1. **Découverte UPnP** : Utilisez une bibliothèque comme `Cling` (Java) ou `python-upnp`
2. **Recherchez** le périphérique `HiMultiScreenServerDevice`
3. **Connectez-vous** au service `VinputControlServer`
4. **Envoyez** des commandes SOAP avec les codes de touches

### Exemple en Python (conceptuel)
```python
import requests

# Adresse du récepteur (trouvée via SSDP)
STB_IP = "192.168.1.xxx"
CONTROL_URL = f"http://{STB_IP}:49152/VinputControlServer/control"

def send_key(key_value):
    soap_body = f'''<?xml version="1.0"?>
    <s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/">
        <s:Body>
            <u:SendKeyCode xmlns:u="urn:schemas-upnp-org:service:VinputControlServer:1">
                <KeyValue>{key_value}</KeyValue>
            </u:SendKeyCode>
        </s:Body>
    </s:Envelope>'''
    
    headers = {
        'Content-Type': 'text/xml; charset="utf-8"',
        'SOAPACTION': '"urn:schemas-upnp-org:service:VinputControlServer:1#SendKeyCode"'
    }
    
    return requests.post(CONTROL_URL, data=soap_body, headers=headers)

# Changer à la chaîne 123
send_key("1")
send_key("2")
send_key("3")
```

---

## 📚 Bibliothèques Utilisées dans l'APK

- **Cling** (org.fourthline.cling) - Implémentation UPnP Java
- **CyberGarage** (org.cybergarage) - Alternative UPnP
- **OkHttp** - Client HTTP
- **Jetty** - Serveur HTTP embarqué
- **Google Protobuf** - Sérialisation de données
- **IJKPlayer** - Lecteur multimédia

---

## ⚠️ Notes Importantes

1. Les ports peuvent varier selon la configuration du récepteur
2. Certaines fonctionnalités nécessitent une authentification
3. Le protocole exact peut différer selon le modèle du récepteur Hisilicon
4. Pour une analyse plus poussée, utilisez **jadx** pour décompiler le code Java

---

*Document généré le 2025-12-07 par analyse de gmscreen.apk*
