# ADRIA Bank - client Flutter

Comptes (creation, solde, statut, credit/debit), paiements, virements,
historique filtre et assistant Gemini utilisant les outils MCP.
Les donnees proviennent des API ; aucun solde n'est simule dans le client.

## Lancer

```powershell
flutter pub get
flutter run
```

Gateway : http://localhost:8080 sur navigateur, http://10.0.2.2:8080 sur
emulateur Android. Pour un telephone, indique l'IP locale du PC dans les
parametres de connexion (icone engrenage), meme reseau Wi-Fi.

Adresse configurable au lancement :

```powershell
flutter run --dart-define=API_URL=http://192.168.1.10:8080
```

Le backend doit etre actif : Eureka, Config Server, account-service,
transaction-service, assistant-service, gateway-service et Kafka.
Aucune cle Gemini n'est embarquee dans le client.

## Apercu web

```powershell
flutter build web
python -m http.server 5300 --bind 127.0.0.1 --directory build/web
```

Puis ouvrir http://localhost:5300.
