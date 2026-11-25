# Grub East (desktop app)

A JavaFX desktop app where students sell products, student organizations post events,
and an admin approves new businesses. Data lives in a MySQL database, so every computer
that installs the app sees the same shops.

## What you need

1. **JDK 21 or newer** (you have 25) and **VS Code** with the *Extension Pack for Java*
2. **XAMPP** (or any MySQL / MariaDB server). Open the XAMPP Control Panel and press **Start** next to **MySQL**.
3. The Maven wrapper. Copy `mvnw`, `mvnw.cmd` and the `.mvn` folder from your old `grubeast`
   project into this folder. (Or install Maven and use `mvn` instead of `.\mvnw`.)

## Run it

Open this folder in VS Code, open a terminal (Ctrl+`) and run:

    .\mvnw javafx:run

The first run downloads JavaFX and the MySQL driver. Then the app:

* connects to MySQL on `localhost:3306` as `root` with no password (XAMPP's default),
* creates the database `grubeast` and all the tables by itself,
* creates the admin account **admin@grubeast.com / admin123** (change it in My profile).

If MySQL uses different settings, the app opens a **Database settings** screen. Fill it in and press
*Save and connect*. The settings are remembered in `C:\Users\<you>\.grubeast\config.properties`.

You can also press the Run button above `Launcher.java` in VS Code.

## Make the installer

    .\build-installer.ps1

* With **WiX Toolset 3.14** installed (https://wixtoolset.org) this creates `installer\GrubEast-1.0.0.exe`,
  a normal Windows installer with a Start menu and desktop shortcut. Java is bundled inside, so the
  computers you install on do not need Java.
* Without WiX it builds a portable folder instead: `installer\GrubEast\GrubEast.exe`.
  Use `.\build-installer.ps1 -Type app-image` to ask for that directly.

## Using one database from many computers

1. Run MySQL on one computer (the "server"). In phpMyAdmin or the MySQL console run:

       CREATE USER 'grubeast'@'%' IDENTIFIED BY 'choose-a-password';
       GRANT ALL ON grubeast.* TO 'grubeast'@'%';

2. Allow port **3306** through that computer's firewall, and make sure MySQL accepts network
   connections (in XAMPP's `my.ini`, `bind-address` must not be `127.0.0.1`).
3. On every other computer, open the app, go to **Log in > Database settings**, type the server
   computer's IP address, the user `grubeast` and the password, then press *Save and connect*.

Only do this on a trusted school network. The database password is saved in a plain file on each computer.

## What is where

| File | What it does |
|------|--------------|
| `Launcher.java`, `App.java` | Starts the window; red header, menu and screen navigation |
| `Db.java` | MySQL connection, creates the tables, query helpers |
| `Dao.java` | Every database question (users, shops, products, orders, events, chat...) |
| `StoreView.java` | Home with Trending, product page, shop page, cart, my preorders |
| `BusinessView.java` | Business dashboard: shop details, products + photos, orders, claimed, reviews |
| `OrgView.java` | Organization dashboard: events + photos, event orders, sponsor applications |
| `EventsView.java` | Public events page and sponsor applications |
| `AdminView.java` | Approve / reject businesses |
| `ChatView.java` | Inbox and chat (checks for new messages every 4 seconds) |
| `ProfileView.java`, `AuthView.java` | Profile, password, log in, sign up |
| `src/main/resources/style.css` | Colors and shapes from the Figma design |
| `database/schema.sql` | The tables, for reading (the app creates them itself) |

## Rules built in

* Passwords are stored as salted PBKDF2 hashes, never as plain text.
* New businesses stay hidden until the admin approves them; rejected ones see the reason and can reapply.
* Students and organizations can review a shop once (1 to 5 stars), use the cart, and place preorders.
* Orders go Pending > Confirmed > Ready > **Claimed** (the business marks it done). Claimed units count as "sold".
* **Trending** shows products from the best-rated shops (only shops with reviews).
* Photos (products, shop logos, events) are resized and stored in the database so every computer sees them.
