package test;

import movienightgui.Database;
import dao.*;
import models.*;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Bütünlük Testleri (Integration Tests)
 * Database sınıfının iş mantığını ve tüm katmanların entegrasyonunu test eder
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IntegrationTests {
    
    private static Connection connection;
    private Database database;
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:integrationtest;DB_CLOSE_DELAY=-1", "sa", "");
        
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
            """);
        }
    }
    
    @BeforeEach
    void setup() {
        database = new Database(connection);
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
     * IT1: Kullanıcı Kayıt ve Giriş İş Mantığı
     */
    @Test
    @Order(1)
    @DisplayName("IT1: Kullanıcı kayıt ve giriş iş mantığı doğru çalışmalı")
    void testUserRegistrationAndLoginBusinessLogic() {
        // Başarılı kayıt
        int status = database.addUser("testuser", "password123", 25);
        assertEquals(0, status, "Geçerli kullanıcı kaydı başarılı olmalı (status=0)");
        
        // Kullanıcı adı boş
        status = database.addUser("", "password", 25);
        assertEquals(1, status, "Boş kullanıcı adı için status=1 dönmeli");
        
        // Kullanıcı adı mevcut
        status = database.addUser("testuser", "password", 25);
        assertEquals(2, status, "Mevcut kullanıcı adı için status=2 dönmeli");
        
        // Şifre boş
        status = database.addUser("newuser", "", 25);
        assertEquals(3, status, "Boş şifre için status=3 dönmeli");
        
        // Yaş kısıtı
        status = database.addUser("younguser", "pass", 16);
        assertEquals(4, status, "18 yaş altı için status=4 dönmeli");
        
        // Giriş testi
        assertTrue(database.validateLogin("testuser", "password123"), "Doğru kimlik bilgileri ile giriş başarılı olmalı");
        assertFalse(database.validateLogin("testuser", "wrongpass"), "Yanlış şifre ile giriş başarısız olmalı");
        assertFalse(database.validateLogin("nonexistent", "pass"), "Olmayan kullanıcı ile giriş başarısız olmalı");
    }
    
    /**
     * IT2: Lobi Oluşturma ve Kullanıcı Yönetimi
     */
    @Test
    @Order(2)
    @DisplayName("IT2: Lobi oluşturma ve kullanıcı yönetimi tam akış")
    void testLobbyCreationAndUserManagement() {
        // Kullanıcılar oluştur
        database.addUser("owner", "pass", 20);
        database.addUser("guest1", "pass", 21);
        database.addUser("guest2", "pass", 22);
        
        // Lobi oluştur
        database.createLobby("owner");
        
        // Kullanıcıları lobiye ekle
        database.addUserToLobby("owner", "owner");
        database.addUserToLobby("owner", "guest1");
        database.addUserToLobby("owner", "guest2");
        
        // Lobideki kullanıcıları kontrol et
        ArrayList<String> usersInLobby = database.getUsersAtLobby("owner");
        assertEquals(3, usersInLobby.size(), "Lobide 3 kullanıcı olmalı");
        assertTrue(usersInLobby.contains("owner"), "Sahibi lobide olmalı");
        assertTrue(usersInLobby.contains("guest1"), "Guest1 lobide olmalı");
        assertTrue(usersInLobby.contains("guest2"), "Guest2 lobide olmalı");
        
        // Kullanıcı çıkar
        database.removeUserFromLobby("owner", "guest1");
        usersInLobby = database.getUsersAtLobby("owner");
        assertEquals(2, usersInLobby.size(), "Lobide 2 kullanıcı kalmalı");
        assertFalse(usersInLobby.contains("guest1"), "Guest1 lobide olmamalı");
        
        // Lobi sahibini kontrol et
        assertEquals("owner", database.getBelongingLobbyOwner("guest2"), "Guest2'nin lobi sahibi owner olmalı");
    }
    
    /**
     * IT3: Davet Sistemi İş Mantığı
     */
    @Test
    @Order(3)
    @DisplayName("IT3: Davet sistemi tam iş mantığı")
    void testInvitationSystemBusinessLogic() {
        // Kullanıcılar oluştur
        database.addUser("sender", "pass", 20);
        database.addUser("receiver1", "pass", 21);
        database.addUser("receiver2", "pass", 22);
        
        // Lobi oluştur
        database.createLobby("sender");
        
        // Davetler gönder
        database.sendInvitationToUser("sender", "receiver1");
        database.sendInvitationToUser("sender", "receiver2");
        
        // Gönderilen davetleri kontrol et
        ArrayList<String> sentInvitations = database.getInvitationsOfUser("sender");
        assertEquals(2, sentInvitations.size(), "2 davet gönderilmiş olmalı");
        
        // Alınan davetleri kontrol et
        ArrayList<String> receivedInvitations1 = database.getInvitiationsForUser("receiver1");
        assertEquals(1, receivedInvitations1.size(), "1 davet alınmış olmalı");
        assertTrue(receivedInvitations1.contains("sender"), "Gönderen 'sender' olmalı");
        
        // Daveti iptal et
        database.removeInvitationFromUser("receiver1", "sender");
        receivedInvitations1 = database.getInvitiationsForUser("receiver1");
        assertEquals(0, receivedInvitations1.size(), "Davet iptal edilmiş olmalı");
        
        // Tüm davetleri temizle
        database.emptyInvitations("sender");
        sentInvitations = database.getInvitationsOfUser("sender");
        assertEquals(0, sentInvitations.size(), "Tüm davetler temizlenmiş olmalı");
    }
    
    /**
     * IT4: Film Öneri Sistemi
     */
    @Test
    @Order(4)
    @DisplayName("IT4: Film öneri sistemi iş mantığı")
    void testMovieSuggestionBusinessLogic() {
        // Kullanıcılar ve lobi oluştur
        database.addUser("user1", "pass", 20);
        database.addUser("user2", "pass", 21);
        database.createLobby("user1");
        database.addUserToLobby("user1", "user1");
        database.addUserToLobby("user1", "user2");
        
        // Film oluştur
        MovieDAO movieDAO = new MovieDAO(connection);
        Movie movie1 = new Movie(0, "TestMovie1", "Desc1", "/t1");
        Movie movie2 = new Movie(0, "TestMovie2", "Desc2", "/t2");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        Movie createdMovie1 = movieDAO.findByTitle("TestMovie1");
        Movie createdMovie2 = movieDAO.findByTitle("TestMovie2");
        
        // Öneri ekle
        database.suggestMovie("user1", "user1", createdMovie1.getId());
        database.suggestMovie("user1", "user2", createdMovie2.getId());
        
        // Önerileri kontrol et
        ArrayList<String> suggestions = database.getSuggestions("user1");
        assertEquals(2, suggestions.size(), "2 öneri olmalı");
        
        // Öneriyi kim yaptı kontrol et
        String suggestedBy = database.getSuggestedByUsername(createdMovie1.getId(), "user1");
        assertEquals("user1", suggestedBy, "İlk film user1 tarafından önerilmiş olmalı");
        
        // Öneri kaldır
        database.removeSuggestion("user1", createdMovie1.getId());
        suggestions = database.getSuggestions("user1");
        assertEquals(1, suggestions.size(), "1 öneri kalmalı");
        
        // Tüm önerileri temizle
        database.emptySuggestions("user1");
        suggestions = database.getSuggestions("user1");
        assertEquals(0, suggestions.size(), "Tüm öneriler temizlenmiş olmalı");
    }
    
    /**
     * IT5: Oylama Sistemi İş Mantığı
     */
    @Test
    @Order(5)
    @DisplayName("IT5: Oylama sistemi tam iş mantığı")
    void testVotingSystemBusinessLogic() {
        // Kullanıcılar oluştur
        database.addUser("owner", "pass", 20);
        database.addUser("voter1", "pass", 21);
        database.addUser("voter2", "pass", 22);
        
        database.createLobby("owner");
        database.addUserToLobby("owner", "owner");
        database.addUserToLobby("owner", "voter1");
        database.addUserToLobby("owner", "voter2");
        
        // Film oluştur
        MovieDAO movieDAO = new MovieDAO(connection);
        Movie movie1 = new Movie(0, "PopularMovie", "Desc1", "/t1");
        Movie movie2 = new Movie(0, "UnpopularMovie", "Desc2", "/t2");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        Movie createdMovie1 = movieDAO.findByTitle("PopularMovie");
        Movie createdMovie2 = movieDAO.findByTitle("UnpopularMovie");
        
        // Öneri ekle
        database.suggestMovie("owner", "owner", createdMovie1.getId());
        database.suggestMovie("owner", "voter1", createdMovie2.getId());
        
        // Oylar - PopularMovie daha çok oy alsın
        database.voteMovie("owner", "owner", createdMovie1.getId());
        database.voteMovie("voter1", "owner", createdMovie1.getId());
        database.voteMovie("voter2", "owner", createdMovie1.getId());
        database.voteMovie("voter1", "owner", createdMovie2.getId());
        
        // Oy sayımı
        var votes = database.getVotes2("owner");
        assertTrue(votes.get(createdMovie1.getId()) > votes.get(createdMovie2.getId()), 
                   "PopularMovie daha fazla oy almalı");
        
        // Kullanıcının oyları
        ArrayList<Integer> voter1Votes = database.getVoteMovieIdsOfUser("owner", "voter1");
        assertEquals(2, voter1Votes.size(), "voter1'in 2 oyu olmalı");
        
        // Oy geri çek
        database.removeVote("voter1", "owner", createdMovie2.getId());
        voter1Votes = database.getVoteMovieIdsOfUser("owner", "voter1");
        assertEquals(1, voter1Votes.size(), "voter1'in 1 oyu kalmalı");
        
        // Film için tüm oyları kaldır
        database.removeVotesForMovie("owner", createdMovie1.getId());
        votes = database.getVotes2("owner");
        assertEquals(0, votes.get(createdMovie1.getId()).intValue(), 
                     "PopularMovie'nin oyu 0 olmalı");
    }
    
    /**
     * IT6: Lobi Hazırlık ve Durum Yönetimi
     */
    @Test
    @Order(6)
    @DisplayName("IT6: Lobi hazırlık ve durum yönetimi")
    void testLobbyReadyStateManagement() {
        // Kullanıcı ve lobi oluştur
        database.addUser("owner", "pass", 20);
        database.createLobby("owner");
        
        // Başlangıçta lobi oylama durumunda
        assertTrue(database.isLobbyStillVoting("owner"), "Lobi başlangıçta oylama durumunda olmalı");
        
        // Lobi hazır yap
        database.setLobbyReady("owner");
        
        // Artık oylama yapılmamalı
        assertFalse(database.isLobbyStillVoting("owner"), "Lobi hazır olduktan sonra oylama bitmeli");
    }
    
    /**
     * IT7: Şifre Güncelleme İş Mantığı
     */
    @Test
    @Order(7)
    @DisplayName("IT7: Kullanıcı şifre güncelleme")
    void testPasswordUpdateBusinessLogic() {
        // Kullanıcı oluştur
        database.addUser("user", "oldpass", 20);
        
        // Eski şifre ile giriş
        assertTrue(database.validateLogin("user", "oldpass"), "Eski şifre ile giriş yapılmalı");
        
        // Şifre güncelle
        database.updatePassword("user", "newpass");
        
        // Yeni şifre ile giriş
        assertTrue(database.validateLogin("user", "newpass"), "Yeni şifre ile giriş yapılmalı");
        assertFalse(database.validateLogin("user", "oldpass"), "Eski şifre ile giriş yapılamamalı");
    }
    
    /**
     * IT8: Film Tür Filtreleme İş Mantığı
     */
    @Test
    @Order(8)
    @DisplayName("IT8: Film tür filtreleme sistemi")
    void testMovieGenreFilteringBusinessLogic() {
        // Türler oluştur
        GenreDAO genreDAO = new GenreDAO(connection);
        Genre action = new Genre(0, "Action");
        Genre comedy = new Genre(0, "Comedy");
        genreDAO.createGenre(action);
        genreDAO.createGenre(comedy);
        
        // Filmler oluştur
        MovieDAO movieDAO = new MovieDAO(connection);
        HasGenreDAO hasGenreDAO = new HasGenreDAO(connection);
        
        Movie movie1 = new Movie(0, "ActionMovie", "Desc", "/t");
        Movie movie2 = new Movie(0, "ComedyMovie", "Desc", "/t");
        Movie movie3 = new Movie(0, "ActionComedy", "Desc", "/t");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        movieDAO.createMovie(movie3);
        
        Movie createdMovie1 = movieDAO.findByTitle("ActionMovie");
        Movie createdMovie2 = movieDAO.findByTitle("ComedyMovie");
        Movie createdMovie3 = movieDAO.findByTitle("ActionComedy");
        
        Genre createdAction = genreDAO.getGenre("Action");
        Genre createdComedy = genreDAO.getGenre("Comedy");
        
        hasGenreDAO.assignGenreToMovie(createdMovie1, createdAction);
        hasGenreDAO.assignGenreToMovie(createdMovie2, createdComedy);
        hasGenreDAO.assignGenreToMovie(createdMovie3, createdAction);
        hasGenreDAO.assignGenreToMovie(createdMovie3, createdComedy);
        
        // Türleri al
        ArrayList<String> genres = database.getGenres();
        assertTrue(genres.contains("Action"), "Action türü listelenebilmeli");
        assertTrue(genres.contains("Comedy"), "Comedy türü listelenebilmeli");
        
        // Action filmlerini filtrele
        ArrayList<String> actionGenres = new ArrayList<>();
        actionGenres.add("Action");
        ArrayList<Integer> actionMovies = database.findMovieIdsByGenres(actionGenres);
        assertTrue(actionMovies.size() >= 2, "En az 2 aksiyon filmi olmalı");
        
        // Film türlerini al
        String genreLabel = database.getMovieGenresLabel(createdMovie3.getId());
        assertTrue(genreLabel.contains("Action"), "Film türleri Action içermeli");
        assertTrue(genreLabel.contains("Comedy"), "Film türleri Comedy içermeli");
    }
    
    /**
     * IT9: Kullanıcı Silme ve Cleanup
     */
    @Test
    @Order(9)
    @DisplayName("IT9: Kullanıcı silme işlemi")
    void testUserDeletionBusinessLogic() {
        // Kullanıcı oluştur
        database.addUser("deleteme", "pass", 20);
        
        // Var olduğunu kontrol et
        assertTrue(database.isUsernameExists("deleteme"), "Kullanıcı mevcut olmalı");
        
        // Sil
        boolean deleted = database.deleteUser("deleteme");
        assertTrue(deleted, "Kullanıcı silinebilmeli");
        
        // Artık yok
        assertFalse(database.isUsernameExists("deleteme"), "Kullanıcı silinmiş olmalı");
    }
    
    /**
     * IT10: Karmaşık Senaryo - Tam Movie Night Akışı
     */
    @Test
    @Order(10)
    @DisplayName("IT10: Tam Movie Night senaryosu")
    void testCompleteMovieNightScenario() {
        // 1. Kullanıcılar kayıt olur
        database.addUser("alice", "pass", 25);
        database.addUser("bob", "pass", 26);
        database.addUser("charlie", "pass", 27);
        
        // 2. Alice lobi oluşturur
        database.createLobby("alice");
        database.addUserToLobby("alice", "alice");
        
        // 3. Alice davetler gönderir
        database.sendInvitationToUser("alice", "bob");
        database.sendInvitationToUser("alice", "charlie");
        
        // 4. Bob ve Charlie davetleri kabul eder
        database.addUserToLobby("alice", "bob");
        database.addUserToLobby("alice", "charlie");
        
        assertEquals(3, database.getUsersAtLobby("alice").size(), "Lobide 3 kullanıcı olmalı");
        
        // 5. Filmler oluşturulur
        MovieDAO movieDAO = new MovieDAO(connection);
        Movie movie1 = new Movie(0, "Film1", "Desc1", "/t1");
        Movie movie2 = new Movie(0, "Film2", "Desc2", "/t2");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        Movie createdMovie1 = movieDAO.findByTitle("Film1");
        Movie createdMovie2 = movieDAO.findByTitle("Film2");
        
        // 6. Kullanıcılar film önerir
        database.suggestMovie("alice", "alice", createdMovie1.getId());
        database.suggestMovie("alice", "bob", createdMovie2.getId());
        
        assertEquals(2, database.getSuggestions("alice").size(), "2 öneri olmalı");
        
        // 7. Herkes oy verir
        database.voteMovie("alice", "alice", createdMovie1.getId());
        database.voteMovie("bob", "alice", createdMovie1.getId());
        database.voteMovie("charlie", "alice", createdMovie1.getId());
        database.voteMovie("bob", "alice", createdMovie2.getId());
        
        // 8. Film1 daha çok oy almalı
        var votes = database.getVotes2("alice");
        assertEquals(3, votes.get(createdMovie1.getId()).intValue(), "Film1'in 3 oyu olmalı");
        assertEquals(1, votes.get(createdMovie2.getId()).intValue(), "Film2'nin 1 oyu olmalı");
        
        // 9. Lobi hazır
        database.setLobbyReady("alice");
        assertFalse(database.isLobbyStillVoting("alice"), "Lobi hazır olmalı");
        
        // 10. Cleanup - Herkes ayrılır
        database.removeUserFromLobby("alice", "alice");
        database.removeUserFromLobby("alice", "bob");
        database.removeUserFromLobby("alice", "charlie");
        
        // Lobi temizliği
        database.emptyInvitations("alice");
        database.emptySuggestions("alice");
        database.emptyVotes("alice");
        database.deleteLobby("alice");
    }
}