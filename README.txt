SZAFA - zwierzatka i kostiumy (/szafa, /szafka)
================================================
Wymagania: Paper 1.21.4+ (Java 21). Bez zewnetrznych pluginow.

KOMPILACJA
  GitHub: wrzuc caly folder do repozytorium - plik .github/workflows/build.yml sam zbuduje
          plugin (zakladka Actions -> Artifacts -> Szafa-jar).
  Lokalnie: mvn clean package  ->  target/Szafa.jar  -> wrzuc do plugins/

RESOURCE PACK (osobny plik Szafa-resourcepack.zip)
  server.properties -> resource-pack=<link do zipa>, resource-pack-sha1=<sha1>, require-resource-pack=true
  Bez packa itemy to zwykle papiery, a kostiumy zwykle zbroje iron.

JAK TO DZIALA
  Zwierzatka i kostiumy to FIZYCZNE ITEMY. Admin daje item komenda, gracz bierze go do reki
  i klika PPM. Item znika, a zwierzatko/kostium laduje w szafie gracza i od razu sie zaklada.
  Czas (np. 7/14/30 dni) liczy sie OD MOMENTU ZALOZENIA. Potem gracz zdejmuje/zaklada
  z /szafa bez potrzeby ponownego dawania itemu, az do wygasniecia.
  Przedluzanie: item "Zwoj przedluzenia" (7/14/30 dni). Gracz otwiera /szafa i klika ZEGAR
  pod zwierzatkiem albo kostiumem (LPM = najmniejszy zwoj, PPM = najwiekszy).

KOMENDY GRACZA
  /szafa, /szafka

KOMENDY ADMINA (uprawnienie szafa.admin, dziala z konsoli)
  /szafaadmin give   <gracz> <pet|kostium> <id> <dni|perm> [ilosc]   - item do ekwipunku
  /szafaadmin scroll <gracz> <dni> [ilosc]                           - Zwoj przedluzenia
  /szafaadmin grant  <gracz> <pet|kostium> <id> <dni|perm>           - prosto do szafy (bez itemu)
  /szafaadmin take   <gracz> <pet|kostium> <id>                      - zabiera z szafy
  /szafaadmin list   <gracz>
  /szafaadmin reload

ID zwierzatek: endermanek, kotek, killerek
ID kostiumow:  koziolek, spiderman, lodowy_straznik

USTAWIENIA (plugins/Szafa/config.yml)
  pet-scale, pet-nametag, pet-yaw-offset (jesli zwierzatko patrzy tylem ustaw 180),
  join-warning-hours, scroll (nazwa i opis zwoju), oraz nazwy/opisy/bonusy kazdego
  zwierzatka i kostiumu.
