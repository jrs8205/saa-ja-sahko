# Kielet: suomi, ruotsi ja englanti

Sovellus näkyy puhelimen kielen mukaan suomeksi, ruotsiksi tai englanniksi. Suomi ja ruotsi näytetään omalla kielellään, kaikki muut kielet englanniksi. Androidin sovelluskohtainen kielivalinta (Asetukset → Sovellukset → Sää & Sähkö → Kieli) ohittaa puhelimen kielen.

## Rakenne

- `app/src/main/res/values/strings.xml` on englanti ja samalla Androidin oletus eli varakieli. `values-fi/` ja `values-sv/` sisältävät suomen ja ruotsin. Lint (`MissingTranslation`) varmistaa, että jokainen avain on kaikissa kolmessa tiedostossa.
- `res/xml/locales_config.xml` ilmoittaa tuetut kielet Androidille, ja `localeFilters` Gradlessa jättää kirjastojen muut kielet pois APK:sta.
- Resurssi `language_tag` kertoo koodille, mikä kieli ratkesi. `AppLanguage` (`domain/Language.kt`) johtaa siitä lokaalin sekä kello-, päivämäärä- ja desimaalimuodot:

| | suomi | ruotsi | englanti |
|---|---|---|---|
| kello | 12.10 | 12.10 | 12:10 |
| päivämäärä | 17.9. | 17.9. | 17 Sep |
| viikonpäivä | Torstaina 17.9. | Torsdag 17.9. | Thursday 17 Sep |
| desimaali | 12,3° | 12,3° | 12.3° |
| yksikkö | snt/kWh | c/kWh | c/kWh |

Suomenruotsi käyttää suomalaisia kello- ja päivämäärämerkintöjä.

- Näkymät lukevat kielen `LocalAppLanguage`-arvosta ja tekstit `stringResource`-kutsuilla. Ilmoitukset ja taustatyöt käyttävät `AppLanguage.of(context)`-funktiota ja `context.getString`-kutsuja, joten ne seuraavat kieltä myös sovelluksen ollessa suljettuna.
- Domain-kerros ei sisällä käännettäviä merkkijonoja. Sääkoodien kuvaukset ovat `WeatherText`-enumin arvoja, virheviestit `AppMessage`-arvoja ja varoitustasot, lähteet ja aikavälit enumeja, jotka `Localization.kt` kuvaa resursseiksi. Jokainen sääkuvaus on kokonainen lause kullakin kielellä, jotta taivutus on oikein.

## Tietolähteet kielen mukaan

- FMI:n CAP-syöte sisältää jokaisen varoituksen suomeksi, ruotsiksi ja englanniksi. Jäsennin valitsee sovelluksen kielen, varalla suomen. Kielen vaihto muuttaa varoitusten sormenjälkiä, joten voimassa olevat varoitukset ilmoitetaan kerran uudelleen uudella kielellä.
- Maanmittauslaitoksen paikkahaku ja lähimmän paikan nimi haetaan `lang`-parametrilla (fi, sv tai en) ja ruotsiksi suositaan ruotsinkielistä nimeä (Borgå, Brunnsparken). Suosikit säilyttävät tallennuskielensä. Androidin osoitehaku käyttää samaa lokaalia.
- Säätiedot ja hinnat ovat kielestä riippumattomia; välimuistit tallentavat raakavastaukset, joten vanha välimuisti toimii kielen vaihdon jälkeen.

## Testit

`LanguageTest` tarkistaa muotoilut, `LocalizationTest` kielen ratkaisun (fi, sv, muut → en), kaikki kolme näkymää ruotsiksi ja englanniksi, tekstien mahtumisen kapealla näytöllä, ilmoitustekstit ja virheviestit. `WarningsTest` kattaa CAP-kielen valinnan ja `PlaceNamesTest` ruotsinkieliset paikannimet.
