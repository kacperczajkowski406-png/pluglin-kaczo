# Chat2Discord (plugin Paper 1.21.4)

Plugin na Twój serwer. Gracz wpisuje np. `/dc cokolwiek`, a:
- bot wysyła na kanał Discorda: **Nick** wysłał: cokolwiek,
- Ty (i inni admini z uprawnieniem `chat2discord.see`) widzisz tę wiadomość w czacie w grze,
- wiadomość trafia też do konsoli serwera.

Działa na Paper (oraz Purpur/Pufferfish). Nie działa na czystym Spigot/Bukkit ani na Forge/Fabric.

## 1. Webhook
Discord → Ustawienia kanału → Integracje → Webhooki → Nowy webhook → Kopiuj URL.

## 2. Budowanie
Wymagane: Java 21 i Gradle 8.x.

```
gradle build
```

Gotowy plik: `build/libs/chat2discord-1.0.0.jar`

## 3. Instalacja
1. Wrzuć jar do folderu `plugins/` serwera i uruchom (lub zrestartuj) serwer.
2. Otwórz `plugins/Chat2Discord/config.yml`, wklej URL w `webhook_url` i ustaw `commands`.
3. Wpisz `/chat2discord reload`.

## Uprawnienia
| Uprawnienie | Domyślnie | Opis |
|---|---|---|
| `chat2discord.use` | wszyscy | użycie komendy wysyłającej na Discord |
| `chat2discord.see` | OP | widzi wiadomości graczy w czacie |
| `chat2discord.admin` | OP | `/chat2discord reload` |

## Konfiguracja
- `commands` – nazwy komend (bez `/`), np. `dc`, `zglos`, `name`.
- `chat.mode` – zwykły czat: `off`, `filter` (tylko z triggerem, np. `!dc`), `all`.
- `format` – wygląd na Discordzie (`{player}`, `{message}`, `{premium}`).
- `admin_view` – podgląd dla adminów w grze i w konsoli.
- `notify_player` / `notify_text` – potwierdzenie dla gracza.
- `premium_check` – pokazuje przy wiadomości, czy gracz jest na koncie premium.

### Jak działa sprawdzanie premium/non-premium
- Jeśli serwer ma `online-mode: true` w `server.properties` – każdy, kto wszedł, jest zweryfikowany przez Mojang/Microsoft, więc status pokazuje się od razu jako Premium, bez żadnego zapytania.
- Jeśli serwer ma `online-mode: false` (tzw. "cracked") – serwer nie weryfikuje graczy, więc plugin pyta publiczne API Mojanga, czy dany **nick** jest zarejestrowany jako konto premium. To sprawdza tylko sam nick, a nie to, czy właśnie ta osoba jest jego właścicielem – na serwerze offline-mode każdy może wejść pod dowolnym, cudzym nickiem, więc traktuj to jako orientacyjną informację, a nie dowód.
- Wyłączysz to ustawiając `premium_check.enabled: false`.

Pingi (@everyone / @here) są wyłączone. Nie używaj nazw komend, które już istnieją (`say`, `msg`, `give`).

## Budowanie bez instalowania czegokolwiek (GitHub Actions)
Wgraj zawartość folderu (razem z `.github`) do nowego repozytorium na github.com, wejdź w zakładkę Actions i po zielonym znaczku pobierz `mod-jar` z sekcji Artifacts.
