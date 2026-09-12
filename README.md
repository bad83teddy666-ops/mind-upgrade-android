# Mind Upgrade Panel — 0.2.2 browser session test

## Current installation and behavior

Install the APK as **Mind Upgrade Panel**. This test uses a separate application
ID (`pl.mindupgrade.android.paneltest`) to avoid replacing the previous APK or
requiring deletion of its settings when debug signing keys differ.

Opening its icon launches the canonical site home in an Android Custom Tab.
The site and its complete Sites-owned sign-in flow run in the same browser
session. No OAuth authorization URL is forwarded from WebView, no cookies or
access tokens are extracted, and no dispatch-owned callback route is replaced.
If Custom Tabs are unavailable, AndroidX falls back to the default browser.
The visible browser toolbar is expected; this is not a native authenticated WebView.

If a previous broken callback is displayed, return to the launcher and tap
**Otwórz Mind Upgrade**, which opens `/` without replaying an OAuth code.
The platform `/callback` 404 itself is not patched by this Android change.

Voice uses the web application's browser microphone and TTS implementation.
The old native bridge is not injected into this mode. This test's Bibi remains
disabled to avoid competing for microphone access. If Bibi is active in the
previous, separately installed APK, turn it off there before testing web voice.

Acceptance on phone: open icon, complete sign-in in that same tab, check panel,
close tab and reopen via launcher, verify session persistence, test web voice,
and verify existing integrations. This has not yet been verified on a phone.

## Previous WebView prototype (0.2.1)

# Mind Upgrade Android — 0.2.1 web test

Wersja Android otwiera istniejący Mind Upgrade w WebView od razu po dotknięciu
ikony. Bibi nie jest wymagane do uruchomienia aplikacji.

## Zmiany 0.2.1

- Mobilny obszar strony, czarne tło i kompaktowy nagłówek. Dotychczasowy pasek
  testowy zastępuje menu ⋮: Bibi, odświeżanie, test głosu, stan i przeglądarka.
- Postęp ładowania, ponowienie po błędzie sieci/serwera i po 30 sekundach oczekiwania.
- Strona logowania pozostaje dostępna przy HTTP 401/403. Aplikacja wyjaśnia,
  że sesja Chrome nie jest sesją WebView. Nie omija uwierzytelniania strony.
- Przycisk systemowy Wstecz wraca w historii strony; zapis ciasteczek po ładowaniu.

Test odbiorczy: otwórz ikoną bez Bibi, zaloguj się, porównaj wygląd z wersją
webową, zamknij i otwórz ponownie, sprawdź MÓW i odpowiedź głosową.
Następnie uruchom bez internetu i użyj „Spróbuj ponownie” po odzyskaniu sieci.
Sprawdź też menu ⋮ oraz przycisk Wstecz. Jeśli logowanie jest blokowane przez
jego dostawcę, potrzebna jest obsługiwana integracja sesji; zmiana wyglądu tego
nie naprawia. Nie potwierdzono jeszcze tych testów na fizycznym telefonie.

Wersja testowa istniejącego Mind Upgrade z lokalnym czuwaniem Bibi i rolą
asystenta Androida. Wymaga sprawdzenia na telefonie zgodnie z instrukcją poniżej.

## Instalacja

Po zielonym wyniku zadania **Android APK** w zakładce Actions pobierz artefakt
**Mind-Upgrade-Android-test**, rozpakuj ZIP i otwórz app-debug.apk na Androidzie.
To pakiet testowy podpisany kluczem debug; kolejne kompilacje mogą wymagać
odinstalowania poprzedniej wersji (usuwa lokalne sesje). Przed regularnym
używaniem potrzebny jest stały, prywatny klucz podpisywania.

## Sprawdzenie na telefonie

1. Otwórz aplikację i sprawdź logowanie do istniejącego Mind Upgrade.
2. Wybierz **Test głosu**. Udziel dostępu do mikrofonu, wypowiedz zdanie
   i sprawdź odczytanie go przez telefon. Ten test nie wymaga klucza API.
3. W Mind Upgrade wybierz MÓW i zadaj pytanie. Sprawdź odpowiedź głosową.
4. Sprawdź połączenia kalendarza i Garmina. Jeśli dostawca odrzuci logowanie
   we wbudowanej przeglądarce, nie obchodź tego ograniczenia. Zapisz komunikat;
   potrzebna będzie integracja logowania przez systemową przeglądarkę
   i bezpieczne przekazanie sesji po stronie istniejącego serwera.
5. Wyjdź z rozmowy: mikrofon rozmowy powinien zostać zwolniony. Jeśli Bibi jest włączone, wraca lokalne czuwanie.

Przycisk **W Chrome** otwiera istniejącą aplikację w przeglądarce. Sesji
Chrome i WebView nie kopiujemy. Zalogowanie w Chrome nie loguje WebView.

## Bezpieczeństwo i ograniczenia

Most głosowy jest wstrzykiwany wyłącznie do głównej ramki dokładnego originu
istniejącej aplikacji. Zewnętrzne strony nie mają dostępu do mikrofonu ani TTS
przez ten most. Brak kluczy, danych użytkownika i sekretów w repozytorium.
Rozpoznawanie pełnych wypowiedzi korzysta z systemowej usługi Androida, która
może wysyłać dźwięk do swojego dostawcy. Lokalne czuwanie Bibi jest osobnym modułem.
Web Push obecnej PWA nie jest zastąpiony natywnymi powiadomieniami w tym APK.

## Budowanie

JDK 17, Gradle 8.11.1, Android SDK 35. Polecenie:
`python3 scripts/prepare-bibi-model.py`, następnie `gradle :app:assembleDebug :app:lintDebug`.
Testy mostu: `node --test tests/*.test.cjs`.
Wymagany aktualny Android System WebView obsługujący dokumentowe skrypty
startowe i bezpieczne wiadomości z kontrolą originu.

## Bibi — wersja 0.2.0 do testu na telefonie

1. Zainstaluj APK z najnowszego zakończonego powodzeniem zadania Android APK.
2. Otwórz aplikację i zaloguj się do Mind Upgrade.
3. Naciśnij **Bibi**, wybierz Mind Upgrade w ustawieniach asystenta Androida i wróć.
4. Naciśnij **Bibi** ponownie, zezwól na mikrofon i powiadomienia. Poczekaj na przygotowanie modelu.
5. Wyjdź na ekran główny i powiedz **Bibi** (bi-bi). Po otwarciu rozmowy podyktuj polecenie.
6. Wyłącz czuwanie przyciskiem Bibi lub akcją Wyłącz w powiadomieniu.

Wykrywanie hasła działa lokalnie przez Vosk. Model angielski rozpoznaje fonetyczne
„bee bee”, odpowiadające wymowie bi-bi. To nie jest model wytrenowany na głosie użytkownika;
skuteczność oraz fałszywe wywołania trzeba sprawdzić na prawdziwym telefonie.
Dźwięk czuwania nie jest zapisywany ani wysyłany. Rozmowa po wywołaniu korzysta
z dotychczasowej internetowej transkrypcji aplikacji.

Czuwanie ma stałe powiadomienie i zużywa baterię. Działa wyłącznie po jego
włączeniu i wybraniu aplikacji jako asystenta. Android może wymagać odblokowania
ekranu; aplikacja nie omija blokady. Po wymuszonym zatrzymaniu trzeba otworzyć
aplikację ponownie. W otwartej aplikacji czuwanie zwalnia mikrofon dla rozmowy;
po wyjściu wraca. Zwykła wersja w przeglądarce nie otrzymuje czuwania w tle.

Sprawdzenie na urządzeniu przed uznaniem funkcji za gotową: wywołanie z pulpitu,
wywołanie przy wygaszonym ekranie, odmowa mikrofonu, wyłączenie czuwania,
kilka kolejnych rozmów, brak fałszywych wywołań podczas normalnej rozmowy i hałasu.
Kompilacja, lint i testy mostka nie zastępują tych testów.

Model `vosk-model-small-en-us-0.15` (Apache-2.0):
https://alphacephei.com/vosk/models — pobierany podczas kompilacji, SHA-256 sprawdzany
w `scripts/prepare-bibi-model.py`. Biblioteka Vosk: https://github.com/alphacep/vosk-api.

Weryfikacja programowa 2026-09-10: pięć testów mostka przeszło. Model rozpoznał
syntetyczne polskie „Bibi” jako „bee bee” (oba słowa z confidence 1.0); nie wywołał
się dla „Dzień dobry”, „Dodaj pięć frezów do listy”, „Zrobię sobie kawę” ani
„Będzie dobrze”. Mała próba syntetyczna nie mierzy skuteczności w realnym hałasie.
