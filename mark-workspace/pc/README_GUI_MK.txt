MarK + TD AI Studio PRO v0.3 - GUI INSTALLER / TEST BUILD

1. Rasapakuvaj ZIP vo PRIVREMENA PAPKA (Downloads ili na HDD) bez da gi menjas starite TD fajlovi.
2. Dvapati klik na Setup_MarK.vbs. Nema CMD i ne se bara Python/Ollama predinstalacija.
3. Odberi HDD/SSD (H:, D:, C: itd.) i destinacija. Ne biraj papka od originalniot TD AI Studio.
4. Klik INSTALIRAJ MarK. Python 3.12 runtime ke se postavi privatno vo <izbran folder>\runtime\python,
   ne se dodava vo PATH i ne se koristi postojnata Python/Ollama instalacija.
5. Novoto AI runtime i Qwen3 VL vision modelot se prezemaat od oficijalni izvori (internet prviot pat).
   Llama: instalaciskata skripta bara stvarni binarni asseti od 20 GitHub releases, ne samo latest.
6. Otkako ke zavrsi, klikni STARTUVAJ MarK, ili ikonata na Desktop.
7. Pri prv start se prikazuva samo ednas 6-cifren PIN. TD UI se otvora kako poseben Edge/Chrome app prozorec.
8. MarK ostanuva lokalen i ne bara Ollama. Python e interna runtime komponenta, ne nov AI model.
9. Site dokumenti od originalniot TD ostanuvaat netaknati. Ako instaliras vrz POSTOECKI MarK,
   stariot mark.sqlite3 i PIN ne se brisat. Setup samo ja osvezuva aplikacijata.
10. TD Booking Android live pair/sync se uste ne e implementiran bez izvoren kod za Booking.
11. Premiere UXP plugin source e vklucen, no Adobe instaliranje/aktiviranje ne e celosno automatizirano.
12. Ako instalacijata prekine, povtorno klikni Setup_MarK.vbs so ISTA destinacija; downloads se obnovuvaat.
    Logot e <izbran folder>\data\install_log.txt.

VAZNO: Ova e WINDOWS GUI test instalacija, ne proverena produkciska EXE aplikacija. 
Golemite komponenti ne se vkluceni vo ZIP, tuku gi prezema setupot. Go zadrzuvame celiot TD web UI.
