# Pehmeä Expressive -käyttöliittymä — 18.9.2026

19.9. katselmointikorjaukset: [kaikkien 15 kohdan tulokset](REVIEW-UI-EXPRESSIVE-ROUND2.md), UI-commit `3dcde84` GitHubin `ui-pehmea-expressive`-haarassa. Tuotannon dynaaminen teemapolku on nyt myös paikallisissa SDK 33/35 -renderöinti- ja kontrastitesteissä. testipuhelimen asennus ja käyttäjälle toimitettu release-APK on kirjattu [jatkomuistioon](../jatkomuistio).

Sovelluksen ilme uudistettiin haarassa `ui-pehmea-expressive`. Muutos koskee vain `fi.omasaasahko.ui`-pakettia, resursseja ja käyttöliittymätestejä. Data, ViewModelit, taustatyöt ja ilmoitukset eivät muuttuneet, eikä `AppScreen(...)`-funktion allekirjoitus.

Suunnitelma: (suunnitelma, ei julkinen). Mockupit: (suunnittelukangas, ei julkinen) (yksityinen kangas, rivit ”C · Pehmeä Expressive”).

## Periaatteet

- Pinnat ovat tasavärisiä lohkoja (`Block`). Ei liukuvärejä, ei 1 px reunuksia.
- Merkitys ilmaistaan tekstillä ja saavutettavuuskuvauksilla; väri tukee niitä: `forecastColors` (FMI sininen, Open-Meteo vihreä), `priceColors` (viisi hintaluokkaa), `warningColors` (keltainen, oranssi, punainen), `sunColors`. Nämä ovat kiinteitä molemmissa teemoissa. Yhteiset lämpimät paletit sijaitsevat `MeaningColors.kt`:ssa.
- Neutraalit pinnat luetaan aina `MaterialTheme.colorScheme`-tokeneista, joten dynaaminen väri toimii edelleen.
- Otsikot ja numerot: Bricolage Grotesque 800 (`DisplayFont`). Muu teksti: Figtree (`BodyFont`). Molemmat ovat muuttuvia fontteja kansiossa `app/src/main/res/font/`, lisenssi OFL (`docs/licenses/`).

## Tokenit

| Asia | Arvo |
|---|---|
| Säteet (`Radius`) | hero 36 dp · block 32 dp · panel 28 dp · tile 24 dp · row 20 dp · `PillShape` 50 % |
| Näytön reunus / lohkojen väli | 20 dp / 10 dp |
| Lämpötila | Sovitus 24–92 sp; suurella fontilla enintään 64 sp, suhteellinen kirjainväli ja rivikorkeus |
| Hinta | `displayMedium` 80 sp, pienenee automaattisesti 40 sp:hen |
| Näytön otsikko / lohkon otsikko | `headlineLarge` 32 sp / `titleLarge` 22 sp |
| Neutraali lohko | `surfaceContainerHigh` |
| Alapalkin tausta | vaalea `inverseSurface`, tumma `surfaceContainerHighest` |
| Valittu pilleri (`ChoiceRow`, päiväpillerit) | `inverseSurface` / `inverseOnSurface` |

Kiinteä teema (`AppTheme(dynamic = false)`) sai uudet tokenit `surfaceContainerHighest`, `inverseSurface` ja `inverseOnSurface`. Vaalean teeman `background` on nyt `#F3F5EF` ja `primaryContainer` `#C8F0DF`.

## Rakenne

| Tiedosto | Sisältö |
|---|---|
| `ui/Theme.kt` | Fontit, typografia, väriteemat |
| `ui/Expressive.kt` | `Radius`, `PillShape`, `isDarkTheme`, `Block`, `SourceDot`, `TonePill`, `RefreshButton`, `LocationPill`, `SettingSwitchRow` |
| `ui/BottomNav.kt` | `AppTab`, `NavTint`, `navTint`, `FloatingNavBar` |
| `ui/WarningColors.kt` | `warningColors(level)` |
| `ui/Components.kt` | `ChoiceRow` pillerivalitsimena, `Notice`, `SectionTitle`, säänsymbolit ennallaan |

`AppScreen` piirtää kiinteän otsikkorivin (sää ja varoitukset: sijaintipilleri, sähkö: otsikko ja hakuaika) sekä kelluvan alapalkin. Alapalkki piilotetaan paikkahaun ajaksi. Valitun välilehden pilleri sävyttyy sisällön mukaan: sää rauhallinen vihreä, sähkö nykyisen hintaluokan väri, varoitukset korkeimman paikallisen varoitustason väri.

## Tietoiset poikkeamat mockupista

1. Hintakaavio on edelleen sivusuunnassa vieritettävä 52 dp:n soluilla ja kiinteällä akselilla (kosketuskohde ≥ 44 dp). Pylväät ovat pyöristettyjä ja nykyinen jakso on merkitty ”Nyt”-pillerillä.
2. Sähkönäkymän otsikko on ”Sähkön hinta”; alapalkissa nimi on ”Sähkö”.
3. Sää-lohkoissa näkyvät myös ennusteaika, UV, päivitysaika ja virheteksti. FMI-aseman havainto on oma lohkonsa laattojen alla.
4. Varoituksissa ilmoitusasetukset ovat varoituslistan alla.
5. Alapalkin fonttiskaalaus on rajattu 1,3-kertaiseksi, koska kolme kiinteää paikkaa ei voi kasvaa tekstin mukana.

## Uusien pintojen ohje

Rakenna uusi pinta `Block`-komponentilla, valitse säde `Radius`-arvoista ja hae merkitysväri yllä luetelluista funktioista. Kytkinrivit tehdään `SettingSwitchRow`-komponentilla (koko rivi on kytkin), valinnat `ChoiceRow`-komponentilla.

## Varmennus

`validate.ps1` 18.9.2026: `assembleDebug`, `testDebugUnitTest` (122 testiä, 0 virhettä, 6 ohitettua live-testiä) ja `lintDebug` (0 virhettä, 4 aiempaa versiovaroitusta). Kuvakaappaukset syntyvät testeistä kansioon `app/build/screenshots/`. Fyysisellä puhelimella ei ole vielä kokeiltu: dynaaminen väri, eleohjauksen ja alapalkin väli sekä vedä-päivittääksesi on tarkistettava laitteella.
