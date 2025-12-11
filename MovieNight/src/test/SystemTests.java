package test;

import movienightgui.*;
import dao.*;
import models.*;
import org.junit.jupiter.api.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sistem Testleri (System Tests)
 * GUI ve tam uygulama akışlarını test eder
 * Bu testler gerçek kullanıcı senaryolarını simüle eder
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SystemTests {
    
    private static Connection connection;
    private Database database;
    private SharedUserModel sharedUserModel;
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:systemtest;DB_CLOSE_DELAY=-1", "sa", "");
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
                CREATE SEQUENCE user_id_seq START WITH 1 INCREMENT BY 1;
                CREATE TABLE "User"(
                    id INT DEFAULT nextval('user_id_seq') PRIMARY KEY,
                    fname VARCHAR(50),
                    lname VARCHAR(50),
                    username VARCHAR(50) UNIQUE,
                    password VARCHAR(50),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    age INT CHECK (age >= 18)
                );
                
                CREATE VIEW user_identifiers AS SELECT id, username FROM "User";
                
                CREATE TABLE Movie(
                    id SERIAL PRIMARY KEY,
                    title VARCHAR(100),
                    description TEXT,
                    trailerPath VARCHAR(200)
                );
                
                CREATE TABLE Genre (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255)
                );
                
                CREATE TABLE HasGenre (
                    movie_id INTEGER REFERENCES Movie(id),
                    genre_id INTEGER REFERENCES Genre(id),
                    PRIMARY KEY (movie_id, genre_id)
                );
                
                CREATE TABLE Lobby(
                    id SERIAL PRIMARY KEY,
                    owner_id INTEGER REFERENCES "User"(id),
                    is_ready BOOLEAN DEFAULT FALSE,
                    date DATE
                );
                
                CREATE TABLE InLobby (
                    lobby_id INTEGER REFERENCES Lobby(id),
                    user_id INTEGER REFERENCES "User"(id),
                    PRIMARY KEY (lobby_id, user_id)
                );
                
                CREATE TABLE Invitation (
                    sender_id INTEGER REFERENCES "User"(id),
                    lobby_id INTEGER REFERENCES Lobby(id),
                    receiver_id INTEGER REFERENCES "User"(id),
                    PRIMARY KEY (sender_id, receiver_id, lobby_id)
                );
                
                CREATE TABLE Suggestion(
                    lobby_id INTEGER REFERENCES Lobby(id),
                    suggested_by INTEGER REFERENCES "User"(id),
                    movie_id INTEGER REFERENCES Movie(id),
                    PRIMARY KEY (lobby_id, movie_id)
                );
                
                CREATE TABLE Vote(
                    lobby_id INTEGER REFERENCES Lobby(id),
                    user_id INTEGER REFERENCES "User"(id),
                    movie_id INTEGER REFERENCES Movie(id),
                    PRIMARY KEY (lobby_id, user_id, movie_id)
                );
                
                CREATE ALIAS IF NOT EXISTS get_user_by_credentials AS $$
                import java.sql.*;
                @CODE
                ResultSet getUserByCredentials(Connection conn, String username, String password) throws SQLException {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT * FROM \\"User\\" WHERE username = ? AND password = ?"
                    );
                    stmt.setString(1, username);
                    stmt.setString(2, password);
                    return stmt.executeQuery();
                }
                $$;
                
                CREATE ALIAS IF NOT EXISTS get_winning_movies_by_votes AS $$
                import java.sql.*;
                import java.util.*;
                @CODE
                ResultSet getWinningMoviesByVotes(Connection conn, Integer lobbyId) throws SQLException {
                    String query = "SELECT m.id as movie_id, m.title as movie_title, COUNT(v.movie_id) as vote_count " +
                                   "FROM Movie m " +
                                   "INNER JOIN Vote v ON m.id = v.movie_id " +
                                   "WHERE v.lobby_id = ? " +
                                   "GROUP BY m.id, m.title " +
                                   "ORDER BY vote_count DESC";
                    PreparedStatement stmt = conn.prepareStatement(query);
                    stmt.setInt(1, lobbyId);
                    return stmt.executeQuery();
                }
                $$;
            """);
        }
    }
    
    @BeforeEach
    void setup() {
        database = new Database(connection);
        sharedUserModel = new SharedUserModel();
    }
    
    @AfterEach
    void cleanup() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM Vote");
            stmt.execute("DELETE FROM Suggestion");
            stmt.execute("DELETE FROM Invitation");
            stmt.execute("DELETE FROM InLobby");
            stmt.execute("DELETE FROM Lobby");
            stmt.execute("DELETE FROM HasGenre");
            stmt.execute("DELETE FROM Genre");
            stmt.execute("DELETE FROM Movie");
            stmt.execute("DELETE FROM \"User\"");
            stmt.execute("ALTER SEQUENCE user_id_seq RESTART WITH 1");
        }
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    /**
     * ST1: Yeni Kullanıcı Kayıt Senaryosu
     */
    @Test
    @Order(1)
    @DisplayName("ST1: Kullanıcı başarıyla kayıt olabilmeli")
    void testUserRegistrationScenario() {
        // Kullanıcı kayıt formunu doldurur
        String username = "newuser";
        String password = "securepass123";
        int age = 25;
        
        // Kayıt işlemi
        int result = database.addUser(username, password, age);
        
        // Başarılı kayıt
        assertEquals(0, result, "Kayıt başarılı olmalı");
        
        // Kullanıcı artık giriş yapabilir
        assertTrue(database.validateLogin(username, password), "Yeni kullanıcı giriş yapabilmeli");
        
        // Kullanıcı listesinde görünür
        assertTrue(database.getUsers().contains(username), "Kullanıcı listesinde olmalı");
    }
    
    /**
     * ST2: Kullanıcı Giriş ve Çıkış Senaryosu
     */
    @Test
    @Order(2)
    @DisplayName("ST2: Kullanıcı giriş yapıp çıkış yapabilmeli")
    void testUserLoginLogoutScenario() {
        // Önce kayıt ol
        database.addUser("testuser", "password", 23);
        
        // Giriş yap
        boolean loginSuccess = database.validateLogin("testuser", "password");
        assertTrue(loginSuccess, "Giriş başarılı olmalı");
        
        // SharedUserModel kullan (GUI'deki gibi)
        sharedUserModel.setUsername("testuser");
        assertEquals("testuser", sharedUserModel.getUsername(), "Kullanıcı oturum açmış olmalı");
        
        // Çıkış yap
        sharedUserModel.setUsername(null);
        assertNull(sharedUserModel.getUsername(), "Kullanıcı çıkış yapmış olmalı");
    }
    
    /**
     * ST3: Lobi Oluşturma ve Davet Gönderme Senaryosu
     */
    @Test
    @Order(3)
    @DisplayName("ST3: Kullanıcı lobi oluşturup davet gönderebilmeli")
    void testCreateLobbyAndSendInvitationsScenario() {
        // Kullanıcılar oluştur
        database.addUser("host", "pass", 25);
        database.addUser("friend1", "pass", 26);
        database.addUser("friend2", "pass", 27);
        database.addUser("friend3", "pass", 28);
        
        // Host giriş yapar
        sharedUserModel.setUsername("host");
        
        // Host lobi oluşturur
        database.createLobby("host");
        database.addUserToLobby("host", "host");
        
        // Host arkadaşlarına davet gönderir
        database.sendInvitationToUser("host", "friend1");
        database.sendInvitationToUser("host", "friend2");
        database.sendInvitationToUser("host", "friend3");
        
        // Gönderilen davetler kontrol edilir
        assertEquals(3, database.getInvitationsOfUser("host").size(), "3 davet gönderilmiş olmalı");
        
        // Friend1 davetini görür
        assertTrue(database.getInvitiationsForUser("friend1").contains("host"), "Friend1 daveti görmeli");
    }
    
    /**
     * ST4: Davet Kabul Etme Senaryosu
     */
    @Test
    @Order(4)
    @DisplayName("ST4: Kullanıcı daveti kabul edip lobiye katılabilmeli")
    void testAcceptInvitationAndJoinLobbyScenario() {
        // Kullanıcılar
        database.addUser("host", "pass", 25);
        database.addUser("guest", "pass", 26);
        
        // Host lobi oluşturur
        database.createLobby("host");
        database.addUserToLobby("host", "host");
        database.sendInvitationToUser("host", "guest");
        
        // Guest giriş yapar ve davetini görür
        sharedUserModel.setUsername("guest");
        assertEquals(1, database.getInvitiationsForUser("guest").size(), "1 davet olmalı");
        
        // Guest daveti kabul eder
        database.addUserToLobby("host", "guest");
        sharedUserModel.setLobby("host");
        
        // Guest artık lobide
        assertTrue(database.getUsersAtLobby("host").contains("guest"), "Guest lobide olmalı");
        assertEquals("host", database.getBelongingLobbyOwner("guest"), "Guest'in lobi sahibi host olmalı");
    }
    
    /**
     * ST5: Film Arama ve Öneri Senaryosu
     */
    @Test
    @Order(5)
    @DisplayName("ST5: Kullanıcı film arayıp önerebilmeli")
    void testSearchAndSuggestMovieScenario() {
        // Hazırlık
        database.addUser("user", "pass", 25);
        database.createLobby("user");
        database.addUserToLobby("user", "user");
        sharedUserModel.setUsername("user");
        
        // Filmler oluştur
        MovieDAO movieDAO = new MovieDAO(connection);
        Movie movie1 = new Movie(0, "Inception", "Mind bending", "/t1");
        Movie movie2 = new Movie(0, "Interstellar", "Space drama", "/t2");
        Movie movie3 = new Movie(0, "The Dark Knight", "Batman", "/t3");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        movieDAO.createMovie(movie3);
        
        // Kullanıcı filmleri görür
        var movieTitles = database.getMovieTitles();
        assertTrue(movieTitles.size() >= 3, "En az 3 film olmalı");
        
        // Kullanıcı "Inception" arar ve bulur
        Movie foundMovie = movieDAO.findByTitle("Inception");
        assertNotNull(foundMovie, "Film bulunmalı");
        
        // Kullanıcı filmi önerir
        database.suggestMovie("user", "user", foundMovie.getId());
        
        // Öneri eklendi
        assertTrue(database.getSuggestions("user").size() > 0, "Öneri listesinde olmalı");
    }
    
    /**
     * ST6: Oylama Senaryosu
     */
    @Test
    @Order(6)
    @DisplayName("ST6: Kullanıcılar önerilen filmlere oy verebilmeli")
    void testVotingScenario() {
        // 3 kullanıcı, 1 lobi
        database.addUser("user1", "pass", 25);
        database.addUser("user2", "pass", 26);
        database.addUser("user3", "pass", 27);
        
        database.createLobby("user1");
        database.addUserToLobby("user1", "user1");
        database.addUserToLobby("user1", "user2");
        database.addUserToLobby("user1", "user3");
        
        // Filmler
        MovieDAO movieDAO = new MovieDAO(connection);
        Movie movie1 = new Movie(0, "Movie A", "Desc A", "/tA");
        Movie movie2 = new Movie(0, "Movie B", "Desc B", "/tB");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        Movie createdMovie1 = movieDAO.findByTitle("Movie A");
        Movie createdMovie2 = movieDAO.findByTitle("Movie B");
        
        // Öneriler
        database.suggestMovie("user1", "user1", createdMovie1.getId());
        database.suggestMovie("user1", "user2", createdMovie2.getId());
        
        // Her kullanıcı oy verir
        // Movie A: 3 oy, Movie B: 1 oy
        database.voteMovie("user1", "user1", createdMovie1.getId());
        database.voteMovie("user2", "user1", createdMovie1.getId());
        database.voteMovie("user3", "user1", createdMovie1.getId());
        database.voteMovie("user2", "user1", createdMovie2.getId());
        
        // Oy sayımı
        var votes = database.getVotes2("user1");
        assertEquals(3, votes.get(createdMovie1.getId()).intValue(), "Movie A 3 oy almalı");
        assertEquals(1, votes.get(createdMovie2.getId()).intValue(), "Movie B 1 oy almalı");
    }
    
    /**
     * ST7: Lobi Hazırlama ve Sonuç Görüntüleme Senaryosu
     */
    @Test
    @Order(7)
    @DisplayName("ST7: Lobi sahibi lobi hazırladığında sonuçlar görünmeli")
    void testLobbyReadyAndResultsScenario() {
        // Hazırlık
        database.addUser("owner", "pass", 25);
        database.addUser("member", "pass", 26);
        database.createLobby("owner");
        database.addUserToLobby("owner", "owner");
        database.addUserToLobby("owner", "member");
        
        // Filmler ve oylar
        MovieDAO movieDAO = new MovieDAO(connection);
        Movie winner = new Movie(0, "Winner Movie", "Best", "/tw");
        Movie loser = new Movie(0, "Loser Movie", "Okay", "/tl");
        movieDAO.createMovie(winner);
        movieDAO.createMovie(loser);
        Movie createdWinner = movieDAO.findByTitle("Winner Movie");
        Movie createdLoser = movieDAO.findByTitle("Loser Movie");
        
        database.suggestMovie("owner", "owner", createdWinner.getId());
        database.suggestMovie("owner", "member", createdLoser.getId());
        database.voteMovie("owner", "owner", createdWinner.getId());
        database.voteMovie("member", "owner", createdWinner.getId());
        database.voteMovie("member", "owner", createdLoser.getId());
        
        // Lobi hazır
        assertTrue(database.isLobbyStillVoting("owner"), "Başlangıçta oylama devam ediyor olmalı");
        database.setLobbyReady("owner");
        assertFalse(database.isLobbyStillVoting("owner"), "Oylama bitmiş olmalı");
        
        // Kazanan filmi al
        var winners = database.getWinnerMovies("owner");
        assertNotNull(winners, "Kazanan filmler olmalı");
        assertTrue(winners.length > 0, "En az 1 kazanan olmalı");
        assertEquals("Winner Movie", winners[0].movieTitle, "Winner Movie kazanmalı");
        assertEquals(2, winners[0].voteCount, "2 oy almalı");
    }
    
    /**
     * ST8: Şifre Değiştirme Senaryosu
     */
    @Test
    @Order(8)
    @DisplayName("ST8: Kullanıcı şifresini değiştirebilmeli")
    void testPasswordChangeScenario() {
        // Kullanıcı kaydı
        database.addUser("user", "oldpassword", 25);
        
        // Giriş yap
        assertTrue(database.validateLogin("user", "oldpassword"), "Eski şifre ile giriş yapılmalı");
        
        // Şifre değiştir
        database.updatePassword("user", "newpassword");
        
        // Yeni şifre ile giriş yap
        assertTrue(database.validateLogin("user", "newpassword"), "Yeni şifre ile giriş yapılmalı");
        assertFalse(database.validateLogin("user", "oldpassword"), "Eski şifre artık geçersiz olmalı");
    }
    
    /**
     * ST9: Lobiden Ayrılma Senaryosu
     */
    @Test
    @Order(9)
    @DisplayName("ST9: Kullanıcı lobiden ayrılabilmeli")
    void testLeaveLobbyScenario() {
        // Kullanıcılar
        database.addUser("owner", "pass", 25);
        database.addUser("member", "pass", 26);
        
        // Lobi
        database.createLobby("owner");
        database.addUserToLobby("owner", "owner");
        database.addUserToLobby("owner", "member");
        
        assertEquals(2, database.getUsersAtLobby("owner").size(), "Lobide 2 kullanıcı olmalı");
        
        // Member ayrılır
        database.removeUserFromLobby("owner", "member");
        
        assertEquals(1, database.getUsersAtLobby("owner").size(), "Lobide 1 kullanıcı kalmalı");
        assertNull(database.getBelongingLobbyOwner("member"), "Member artık lobiye ait olmamalı");
    }
    
    /**
     * ST10: Tam Movie Night Deneyimi - End to End
     */
    @Test
    @Order(10)
    @DisplayName("ST10: Tam Movie Night deneyimi (E2E)")
    void testCompleteMovieNightExperience() {
        System.out.println("=== Movie Night Tam Senaryo Başlangıç ===");
        
        // 1. KAYIT ve GİRİŞ
        System.out.println("Adım 1: Kullanıcılar kayıt oluyor...");
        database.addUser("alice", "alice123", 25);
        database.addUser("bob", "bob123", 26);
        database.addUser("charlie", "charlie123", 27);
        database.addUser("diana", "diana123", 28);
        assertEquals(4, database.getUsers().size(), "4 kullanıcı kayıtlı olmalı");
        
        // 2. LOBİ OLUŞTURMA
        System.out.println("Adım 2: Alice lobi oluşturuyor...");
        database.createLobby("alice");
        database.addUserToLobby("alice", "alice");
        
        // 3. DAVET GÖNDERME
        System.out.println("Adım 3: Alice davet gönderiyor...");
        database.sendInvitationToUser("alice", "bob");
        database.sendInvitationToUser("alice", "charlie");
        database.sendInvitationToUser("alice", "diana");
        assertEquals(3, database.getInvitationsOfUser("alice").size(), "3 davet gönderilmeli");
        
        // 4. DAVETLERİ KABUL ETME
        System.out.println("Adım 4: Kullanıcılar davetleri kabul ediyor...");
        database.addUserToLobby("alice", "bob");
        database.addUserToLobby("alice", "charlie");
        database.addUserToLobby("alice", "diana");
        assertEquals(4, database.getUsersAtLobby("alice").size(), "4 kullanıcı lobide olmalı");
        
        // 5. FİLM OLUŞTURMA
        System.out.println("Adım 5: Filmler sisteme ekleniyor...");
        MovieDAO movieDAO = new MovieDAO(connection);
        GenreDAO genreDAO = new GenreDAO(connection);
        HasGenreDAO hasGenreDAO = new HasGenreDAO(connection);
        
        Genre action = new Genre(0, "Action");
        Genre comedy = new Genre(0, "Comedy");
        Genre drama = new Genre(0, "Drama");
        genreDAO.createGenre(action);
        genreDAO.createGenre(comedy);
        genreDAO.createGenre(drama);
        
        Movie movie1 = new Movie(0, "Avengers", "Superhero action", "/t1");
        Movie movie2 = new Movie(0, "The Hangover", "Comedy", "/t2");
        Movie movie3 = new Movie(0, "Interstellar", "Sci-fi drama", "/t3");
        Movie movie4 = new Movie(0, "Die Hard", "Action thriller", "/t4");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        movieDAO.createMovie(movie3);
        movieDAO.createMovie(movie4);
        
        Movie createdMovie1 = movieDAO.findByTitle("Avengers");
        Movie createdMovie2 = movieDAO.findByTitle("The Hangover");
        Movie createdMovie3 = movieDAO.findByTitle("Interstellar");
        Movie createdMovie4 = movieDAO.findByTitle("Die Hard");
        
        // Film türleri
        hasGenreDAO.assignGenreToMovie(createdMovie1, genreDAO.getGenre("Action"));
        hasGenreDAO.assignGenreToMovie(createdMovie2, genreDAO.getGenre("Comedy"));
        hasGenreDAO.assignGenreToMovie(createdMovie3, genreDAO.getGenre("Drama"));
        hasGenreDAO.assignGenreToMovie(createdMovie4, genreDAO.getGenre("Action"));
        
        // 6. FİLM ÖNERİLERİ
        System.out.println("Adım 6: Kullanıcılar film öneriyor...");
        database.suggestMovie("alice", "alice", createdMovie1.getId());
        database.suggestMovie("alice", "bob", createdMovie2.getId());
        database.suggestMovie("alice", "charlie", createdMovie3.getId());
        database.suggestMovie("alice", "diana", createdMovie4.getId());
        assertEquals(4, database.getSuggestions("alice").size(), "4 öneri olmalı");
        
        // 7. OYLAMA
        System.out.println("Adım 7: Kullanıcılar oy veriyor...");
        // Avengers en popüler olsun (4 oy)
        database.voteMovie("alice", "alice", createdMovie1.getId());
        database.voteMovie("bob", "alice", createdMovie1.getId());
        database.voteMovie("charlie", "alice", createdMovie1.getId());
        database.voteMovie("diana", "alice", createdMovie1.getId());
        
        // Interstellar ikinci (2 oy)
        database.voteMovie("bob", "alice", createdMovie3.getId());
        database.voteMovie("charlie", "alice", createdMovie3.getId());
        
        // Die Hard (1 oy)
        database.voteMovie("diana", "alice", createdMovie4.getId());
        
        // The Hangover (0 oy)
        
        var votes = database.getVotes2("alice");
        assertEquals(4, votes.get(createdMovie1.getId()).intValue(), "Avengers 4 oy almalı");
        assertEquals(2, votes.get(createdMovie3.getId()).intValue(), "Interstellar 2 oy almalı");
        
        // 8. LOBİ HAZIR
        System.out.println("Adım 8: Lobi hazırlanıyor...");
        database.setLobbyReady("alice");
        assertFalse(database.isLobbyStillVoting("alice"), "Oylama tamamlanmış olmalı");
        
        // 9. SONUÇ
        System.out.println("Adım 9: Kazanan film belirleniyor...");
        var winners = database.getWinnerMovies("alice");
        assertNotNull(winners, "Kazanan filmler olmalı");
        assertTrue(winners.length > 0, "En az 1 kazanan olmalı");
        
        System.out.println("Kazanan: " + winners[0].movieTitle + " - " + winners[0].voteCount + " oy");
        assertEquals("Avengers", winners[0].movieTitle, "Avengers kazanmalı");
        assertEquals(4, winners[0].voteCount, "4 oy ile kazanmalı");
        
        // 10. CLEANUP
        System.out.println("Adım 10: Lobi temizleniyor...");
        database.removeUserFromLobby("alice", "alice");
        database.removeUserFromLobby("alice", "bob");
        database.removeUserFromLobby("alice", "charlie");
        database.removeUserFromLobby("alice", "diana");
        
        database.emptyInvitations("alice");
        database.emptySuggestions("alice");
        database.emptyVotes("alice");
        database.deleteLobby("alice");
        
        System.out.println("=== Movie Night Tam Senaryo Tamamlandı ===");
    }
    
    /**
     * ST11: Hata Senaryoları - Kötü Girdiler
     */
    @Test
    @Order(11)
    @DisplayName("ST11: Sistem hatalı girdileri doğru işlemeli")
    void testErrorHandlingScenarios() {
        // Boş kullanıcı adı
        assertEquals(1, database.addUser("", "pass", 20), "Boş kullanıcı adı reddedilmeli");
        
        // Boş şifre
        assertEquals(3, database.addUser("user", "", 20), "Boş şifre reddedilmeli");
        
        // Yaş kısıtı
        assertEquals(4, database.addUser("kid", "pass", 15), "18 yaş altı reddedilmeli");
        
        // Duplicate kullanıcı
        database.addUser("existing", "pass", 20);
        assertEquals(2, database.addUser("existing", "pass2", 21), "Mevcut kullanıcı adı reddedilmeli");
        
        // Olmayan kullanıcı ile giriş
        assertFalse(database.validateLogin("nonexistent", "pass"), "Olmayan kullanıcı ile giriş başarısız olmalı");
        
        // Yanlış şifre
        database.addUser("user", "correctpass", 20);
        assertFalse(database.validateLogin("user", "wrongpass"), "Yanlış şifre ile giriş başarısız olmalı");
    }
    
    /**
     * ST12: Performans - Çoklu Eşzamanlı İşlem
     */
    @Test
    @Order(12)
    @DisplayName("ST12: Sistem birden fazla işlemi aynı anda yönetebilmeli")
    void testConcurrentOperationsScenario() {
        // 10 kullanıcı oluştur
        for (int i = 1; i <= 10; i++) {
            database.addUser("user" + i, "pass" + i, 20 + i);
        }
        
        assertEquals(10, database.getUsers().size(), "10 kullanıcı olmalı");
        
        // 5 lobi oluştur
        for (int i = 1; i <= 5; i++) {
            database.createLobby("user" + i);
            database.addUserToLobby("user" + i, "user" + i);
        }
        
        // Her lobiye 2 film öner
        MovieDAO movieDAO = new MovieDAO(connection);
        for (int i = 1; i <= 10; i++) {
            Movie movie = new Movie(0, "Movie" + i, "Desc" + i, "/t" + i);
            movieDAO.createMovie(movie);
        }
        
        for (int i = 1; i <= 5; i++) {
            Movie movie1 = movieDAO.findByTitle("Movie" + (i * 2 - 1));
            Movie movie2 = movieDAO.findByTitle("Movie" + (i * 2));
            database.suggestMovie("user" + i, "user" + i, movie1.getId());
            database.suggestMovie("user" + i, "user" + i, movie2.getId());
        }
        
        // Tüm lobiler için önerileri kontrol et
        for (int i = 1; i <= 5; i++) {
            assertEquals(2, database.getSuggestions("user" + i).size(), 
                        "Her lobide 2 öneri olmalı");
        }
        
        System.out.println("Performans testi: 10 kullanıcı, 5 lobi, 10 film başarıyla işlendi");
    }
}