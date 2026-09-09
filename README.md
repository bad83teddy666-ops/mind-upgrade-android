# Etap 2 — budzik głosowy (wersja testowa)

Dodano natywny ekran „BUDZIK 6:01”: zgody na powiadomienia i dokładne alarmy,
test za minutę, codzienny alarm o 6:01 Europe/Warsaw i wyłącznik.
Budzik jest domyślnie wyłączony. Odtwarza stałą sentencję polskim systemowym
TTS, z kanałem audio alarmu, bez API AI i bez mikrofonu. Nie uruchamia interfejsu
na siłę nad ekranem blokady. Rozmowa i integracje pozostają w istniejącym WebView.

## Krótki test użytkownika

1. Instalacja dopiero po wyjaśnieniu ostrzeżenia antywirusa dla konkretnego APK.
2. Otwórz BUDZIK 6:01 i udziel obu zgód wskazanych na ekranie.
3. Ustaw głośność alarmów, wybierz Test za minutę, zablokuj ekran.
4. Potwierdź słyszalne powitanie, działanie STOP, następnie włącz 6:01.
5. Sprawdź kolejny poranek i restart telefonu. Po restarcie wymagane pierwsze
   odblokowanie. Wymuszone zatrzymanie blokuje alarmy do ponownego otwarcia.

Alarm używa AlarmManager.setAlarmClock, ponawia następny dzień i odtwarza
przez krótką usługę mediaPlayback. Ma limit 45 sekund, STOP, zwalnia blokadę
CPU i audio focus. Brak stałego procesu, nasłuchu Bibi lub automatycznej rozmowy.
Tryb Nie przeszkadzać, głośność, wybrany silnik TTS i ograniczenia producenta
wymagają testu na urządzeniu. Nie zmienia głośności ani ustawień DND za użytkownika.
Wynik zakończenia TTS nie stanowi potwierdzenia, że użytkownik usłyszał dźwięk.

Ostrzeżenie Avasta z poprzedniego APK pozostaje niewyjaśnione. Nowa kompilacja
nie jest wynikiem skanowania ani dowodem rozwiązania problemu. Nadal podpis debug;
stały prywatny podpis release i bezpieczne logowanie natywne to osobny etap.

---

# Mind Upgrade Android — etap 1

Wersja testowa istniejącego Mind Upgrade z natywnym rozpoznawaniem wypowiedzi
po naciśnięciu MÓW i odpowiedziami głosowymi Androida. Nie zawiera jeszcze
wybudzania Bibi ani nasłuchu po zablokowaniu ekranu.

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
5. Zablokuj ekran podczas nagrywania: mikrofon powinien zostać zatrzymany.

Przycisk **W Chrome** otwiera istniejącą aplikację w przeglądarce. Sesji
Chrome i WebView nie kopiujemy. Zalogowanie w Chrome nie loguje WebView.

## Bezpieczeństwo i ograniczenia

Most głosowy jest wstrzykiwany wyłącznie do głównej ramki dokładnego originu
istniejącej aplikacji. Zewnętrzne strony nie mają dostępu do mikrofonu ani TTS
przez ten most. Brak kluczy, danych użytkownika i sekretów w repozytorium.
Rozpoznawanie pełnych wypowiedzi korzysta z systemowej usługi Androida, która
może wysyłać dźwięk do swojego dostawcy. To nie jest lokalny detektor Bibi.
Web Push obecnej PWA nie jest zastąpiony natywnymi powiadomieniami w tym APK.

## Budowanie

JDK 17, Gradle 8.11.1, Android SDK 35. Polecenie:
`gradle :app:assembleDebug :app:lintDebug`.
Testy mostu: `node --test tests/*.test.cjs`.
Wymagany aktualny Android System WebView obsługujący dokumentowe skrypty
startowe i bezpieczne wiadomości z kontrolą originu.

## Następny etap

Po weryfikacji logowania i rozmowy: osobny lokalny detektor słowa Bibi,
usługa mikrofonu uruchamiana jawnie z aplikacji, stałe powiadomienie z STOP,
przekazywanie mikrofonu do rozmowy i powrót do czuwania. Rozmowa przy blokadzie
wymaga natywnego klienta sesji/API; nie należy polegać na wykonywaniu JS
w uśpionym WebView. Brak restartowania nasłuchu bez wiedzy użytkownika.
