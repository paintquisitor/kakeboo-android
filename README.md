# Kakebo — księga domowych rachunków

Natywna aplikacja Android do zarządzania budżetem domowym — plan miesiąca, dziennik, podsumowania tygodni i bilans miesiąca.
Kotlin, Jetpack Compose (Material 3), ViewModel i lokalna baza Room/SQLite.
Android 8.0 (API 26) lub nowszy. Domyślna waluta: PLN.

## Uruchomienie

Otwórz ten katalog w Android Studio i zsynchronizuj Gradle. Wybierz JDK 17
lub 21 (można użyć JDK dołączonego do Android Studio) oraz zainstaluj Android
SDK 36. Uruchom konfigurację `app` na emulatorze lub telefonie.

Z terminala, z ustawionym `JAVA_HOME` i `ANDROID_HOME` lub lokalnym
`local.properties` zawierającym `sdk.dir=ścieżka/do/sdk`:

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
./gradlew connectedDebugAndroidTest
```

Druga komenda wymaga uruchomionego emulatora lub podłączonego telefonu.

## Struktura

```text
app/
  schemas/                       # wersjonowane schematy Room
  src/main/
    java/pl/kakebo/
      KakeboApplication.kt       # tworzenie bazy i repozytorium
      MainActivity.kt            # punkt wejścia
      data/local/                # encje, DAO, baza danych
      data/repository/           # dostęp do danych dla ViewModel
      domain/                    # kwoty i obliczenia budżetu
      ui/home/                   # plan miesiąca, formularze i ViewModel
      ui/diary/                  # codzienne wydatki i kategorie
      ui/weekly/                 # podsumowania tygodni
      ui/monthly/                # bilans miesiąca i refleksja
      ui/theme/                  # motyw Compose
    res/                         # teksty, ikona, konfiguracja Androida
  src/test/                      # walidacja kwot i obliczenia budżetu
  src/androidTest/               # zapis, migracja bazy i formularze na urządzeniu
gradle/                          # wrapper i katalog wersji zależności
```

## Dane i dalsza rozbudowa

Baza `kakebo.db` znajduje się w prywatnym katalogu aplikacji. Manifest nie
deklaruje dostępu do internetu; kopie zapasowe i transfer danych przez system
są wyłączone. Odinstalowanie aplikacji lub wyczyszczenie jej danych usuwa bazę.
Eksport i odzyskiwanie danych nie są jeszcze zaimplementowane.

Wpis przechowuje dodatnią kwotę w groszach (`Long`), typ przychód/wydatek,
datę jako `LocalDate.toEpochDay()`, notatkę i podkategorię wydatku.
Kategoria główna wynika z podkategorii; identyfikatory enumów są trwałe.
Schematy bazy są wersjonowane w `app/schemas`; zmiany schematu wymagają
zwiększenia wersji i migracji zachowującej dane. Nie używamy destrukcyjnego
odtwarzania bazy.

Ekran planu pozwala wybierać miesiąc, dodawać, edytować i usuwać planowane
wpływy oraz stałe wydatki z datą. Można zapisać kwotę planowanych oszczędności
i postanowienia. Dostępny budżet to wpływy minus koszty stałe i cel
oszczędnościowy; ujemny wynik sygnalizuje niedobór.

Koszty stałe wpisane w Planie od razu pomniejszają budżet i nie wymagają
potwierdzania płatności. Nie wpisujemy ich ponownie w Dzienniku.
Przy pierwszym otwarciu miesiąca bez zapisanego planu aplikacja kopiuje ostatni
wcześniejszy plan: wpływy, koszty stałe, cel oszczędnościowy i postanowienia.
Daty dopasowuje do długości miesiąca. Każdy miesiąc można zmieniać niezależnie;
istniejące plany nie są nadpisywane. Pozycje jednorazowe należy usunąć z nowego planu.
Zakładka Dziennik pozwala dodawać, edytować
i usuwać wydatki z datą, kwotą, opisem i podkategorią. Pokazuje sumy dzienne,
miesięczne oraz dla pięciu kategorii. Hobby i kultura są oddzielone od
jedzenia poza domem i wyjść.

Do wydatku można dołączyć jedno zdjęcie paragonu (do 25 MB) z pliku lub aparatu,
obejrzeć je z powiększeniem, zamienić albo usunąć. Zdjęcia są kopiowane do
prywatnego katalogu aplikacji na telefonie; zmiany załącznika zatwierdza zapis wydatku.

Baza w wersji 6 ma migracje z wersji 1–5 zachowujące wpisy, plany i refleksje.
Starsze wydatki pozostają bez kategorii, którą można uzupełnić w edycji.
Pozostały budżet to wpływy minus cel oszczędnościowy, koszty stałe z Planu
i pozostałe wydatki z Dziennika. Powiązania utworzone w starszej wersji
są zachowane, aby nie naliczać tych samych kosztów dwukrotnie.

Zakładka Tygodnie pokazuje sumy dzienne i według kategorii, porównanie tygodni,
pozostały budżet. Tygodnie biegną od
poniedziałku do niedzieli i są przycinane do granic miesiąca.
Zakładka Bilans pokazuje wpływy, koszty stałe, pozostałe wydatki, sumy kategorii
i kwotę pozostałą z wpływów przed odłożeniem oszczędności. Porównuje tę kwotę
z celem. Dla niezakończonego miesiąca wynik jest wstępny i zależy od obecnych
wpisów. Refleksja (co się udało, co poprawić, postanowienia na kolejny miesiąc)
jest zapisywana lokalnie, osobno dla każdego miesiąca. Eksport i import kopii
pozostają dalszym etapem.

Konfiguracja bazuje na dokumentacji [Android Gradle Plugin](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
i [Room](https://developer.android.com/jetpack/androidx/releases/room).

## Ikony

[Heroicons](https://github.com/tailwindlabs/heroicons), wariant 24 px outline, są dołączone
lokalnie jako Android VectorDrawable. Źródłowe ścieżki SVG pozostają bez zmian.
Licencja MIT i identyfikator wersji źródłowej znajdują się w
`app/src/main/assets/third_party/heroicons/` i są dołączane do APK.
