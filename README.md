# Mind Upgrade Android — Bibi 0.2.0

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
