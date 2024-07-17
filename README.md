### Project Description
HOTELIER è un **progetto universitario** realizzato per il corso di Laboratorio III della triennale di informatica presso l'Università di Pisa. Hotelier è un servizio semplificato di recensioni e classifiche per hotel ispirato a TripAdvisor. Il progetto mira a implementare funzionalità principali come la ricerca degli hotel, l'invio delle recensioni e la classificazione degli utenti basata sulle recensioni.

Aspetti principali:

- Vengono gestiti solo gli hotel situati nelle città capoluogo delle 20 regioni italiane.
- Gli utenti possono inviare recensioni con un punteggio globale e punteggi specifici per categoria.
- Le classifiche sono calcolate in base alla qualità, quantità e recentità delle recensioni.
- Gli utenti guadagnano distintivi in base al numero di recensioni inviate, indicando il loro livello di esperienza.

### Funzionalità di Base
- Client:
  - Interfaccia a Linea di Comando (CLI) per l'interazione con l'utente.
  - Funzioni per la registrazione, login/logout, ricerca hotel, invio recensioni e visualizzazione distintivi.
- Server:
  - Carica i dati degli hotel da un file JSON.
  - Gestisce la registrazione degli utenti, il login e le recensioni.
  - Aggiorna le classifiche degli hotel e i distintivi degli utenti.
  - Invia notifiche quando le classifiche degli hotel cambiano.

