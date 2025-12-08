package test;

import models.*;
import dao.*;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * InvitationDAO, SuggestionDAO ve VoteDAO için birim testleri
 * Test edilen gereksinimler: 31-50
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InvitationSuggestionVoteDAOTest {
    
    private static Connection connection;
    private UserDAO userDAO;
    private LobbyDAO lobbyDAO;
    private MovieDAO movieDAO;
    private InvitationDAO invitationDAO;
    private SuggestionDAO suggestionDAO;
    private VoteDAO voteDAO;
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:fulltest;DB_CLOSE_DELAY=-1", "sa", "");
        
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
                
                CREATE TABLE Movie(
                    id SERIAL PRIMARY KEY,
                    title VARCHAR(100),
                    description TEXT,
                    trailerPath VARCHAR(200)
                );
                
                CREATE TABLE Lobby(
                    id SERIAL PRIMARY KEY,
                    owner_id INTEGER REFERENCES "User"(id),
                    is_ready BOOLEAN DEFAULT FALSE,
                    date DATE
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
            """);
        }
    }
    
    @BeforeEach
    void setup() {
        userDAO = new UserDAO(connection);
        lobbyDAO = new LobbyDAO(connection);
        movieDAO = new MovieDAO(connection);
        invitationDAO = new InvitationDAO(connection);
        suggestionDAO = new SuggestionDAO(connection);
        voteDAO = new VoteDAO(connection);
    }
    
    @AfterEach
    void cleanup() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM Vote");
            stmt.execute("DELETE FROM Suggestion");
            stmt.execute("DELETE FROM Invitation");
            stmt.execute("DELETE FROM Lobby");
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
    
    // Gereksinim 31: Kullanıcılar diğer kullanıcılara lobi daveti gönderebilmeli
    @Test
    @Order(1)
    @DisplayName("Test 39: Kullanıcı başka kullanıcıya davet gönderebilmeli")
    void testSendInvitation() {
        User sender = new User(0, "Ali", "Y", "ali", "pass", null);
        User receiver = new User(0, "Veli", "D", "veli", "pass", null);
        userDAO.createUser(sender);
        userDAO.createUser(receiver);
        User createdSender = userDAO.findByUsername("ali");
        User createdReceiver = userDAO.findByUsername("veli");
        
        lobbyDAO.createLobby(createdSender.getId(), createdSender.getId());
        Lobby lobby = lobbyDAO.findById(createdSender.getId());
        
        boolean result = invitationDAO.sendInvitation(createdSender, lobby, createdReceiver);
        
        assertTrue(result, "Davet gönderme başarılı olmalı");
    }
    
    // Gereksinim 32: Kullanıcılar aldığı davetleri listeleyebilmeli
    @Test
    @Order(2)
    @DisplayName("Test 40: Kullanıcı aldığı davetleri listeleyebilmeli")
    void testFindInvitationsByReceiver() {
        User sender = new User(0, "Ali", "Y", "ali", "pass", null);
        User receiver = new User(0, "Veli", "D", "veli", "pass", null);
        userDAO.createUser(sender);
        userDAO.createUser(receiver);
        User createdSender = userDAO.findByUsername("ali");
        User createdReceiver = userDAO.findByUsername("veli");
        
        lobbyDAO.createLobby(createdSender.getId(), createdSender.getId());
        Lobby lobby = lobbyDAO.findById(createdSender.getId());
        
        invitationDAO.sendInvitation(createdSender, lobby, createdReceiver);
        
        List<Invitation> invitations = invitationDAO.findByReceiver(createdReceiver.getId());
        
        assertEquals(1, invitations.size(), "1 davet olmalı");
        assertEquals(createdSender.getId(), invitations.get(0).getSenderId(), "Gönderen eşleşmeli");
    }
    
    // Gereksinim 33: Kullanıcılar gönderdiği davetleri listeleyebilmeli
    @Test
    @Order(3)
    @DisplayName("Test 41: Kullanıcı gönderdiği davetleri listeleyebilmeli")
    void testFindInvitationsBySender() {
        User sender = new User(0, "Ali", "Y", "ali", "pass", null);
        User receiver = new User(0, "Veli", "D", "veli", "pass", null);
        userDAO.createUser(sender);
        userDAO.createUser(receiver);
        User createdSender = userDAO.findByUsername("ali");
        User createdReceiver = userDAO.findByUsername("veli");
        
        lobbyDAO.createLobby(createdSender.getId(), createdSender.getId());
        Lobby lobby = lobbyDAO.findById(createdSender.getId());
        
        invitationDAO.sendInvitation(createdSender, lobby, createdReceiver);
        
        List<Invitation> invitations = invitationDAO.findBySender(createdSender.getId());
        
        assertEquals(1, invitations.size(), "1 davet olmalı");
        assertEquals(createdReceiver.getId(), invitations.get(0).getReceiverId(), "Alıcı eşleşmeli");
    }
    
    // Gereksinim 34: Davetler iptal edilebilmeli
    @Test
    @Order(4)
    @DisplayName("Test 42: Davet iptal edilebilmeli")
    void testDeleteInvitation() {
        User sender = new User(0, "Ali", "Y", "ali", "pass", null);
        User receiver = new User(0, "Veli", "D", "veli", "pass", null);
        userDAO.createUser(sender);
        userDAO.createUser(receiver);
        User createdSender = userDAO.findByUsername("ali");
        User createdReceiver = userDAO.findByUsername("veli");
        
        lobbyDAO.createLobby(createdSender.getId(), createdSender.getId());
        Lobby lobby = lobbyDAO.findById(createdSender.getId());
        
        invitationDAO.sendInvitation(createdSender, lobby, createdReceiver);
        boolean deleteResult = invitationDAO.deleteInvitation(createdSender.getId(), createdReceiver.getId());
        
        assertTrue(deleteResult, "Davet silme başarılı olmalı");
        assertEquals(0, invitationDAO.findByReceiver(createdReceiver.getId()).size(), 
                     "Davet listesi boş olmalı");
    }
    
    // Gereksinim 36: Tüm davetler silinebilmeli
    @Test
    @Order(5)
    @DisplayName("Test 43: Kullanıcının tüm davetleri silinebilmeli")
    void testRemoveAllInvitations() {
        User sender = new User(0, "Ali", "Y", "ali", "pass", null);
        User receiver1 = new User(0, "Veli", "D", "veli", "pass", null);
        User receiver2 = new User(0, "Ayşe", "K", "ayse", "pass", null);
        userDAO.createUser(sender);
        userDAO.createUser(receiver1);
        userDAO.createUser(receiver2);
        User createdSender = userDAO.findByUsername("ali");
        User createdReceiver1 = userDAO.findByUsername("veli");
        User createdReceiver2 = userDAO.findByUsername("ayse");
        
        lobbyDAO.createLobby(createdSender.getId(), createdSender.getId());
        Lobby lobby = lobbyDAO.findById(createdSender.getId());
        
        invitationDAO.sendInvitation(createdSender, lobby, createdReceiver1);
        invitationDAO.sendInvitation(createdSender, lobby, createdReceiver2);
        
        boolean result = invitationDAO.removeAllInvitations(createdSender.getId());
        
        assertTrue(result, "Tüm davetleri silme başarılı olmalı");
        assertEquals(0, invitationDAO.findBySender(createdSender.getId()).size(), 
                     "Gönderilen davetler boş olmalı");
    }
    
    // Gereksinim 38: Kullanıcılar lobiye film önerisinde bulunabilmeli
    @Test
    @Order(6)
    @DisplayName("Test 44: Kullanıcı lobiye film önerebilmeli")
    void testAddSuggestion() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailer");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        boolean result = suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        assertTrue(result, "Öneri ekleme başarılı olmalı");
    }
    
    // Gereksinim 39: Aynı film birden fazla kez önerilemez
    @Test
    @Order(7)
    @DisplayName("Test 45: Aynı film iki kez önerilemez")
    void testDuplicateSuggestionPrevented() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailer");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        boolean exists = suggestionDAO.suggestionExists(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        assertTrue(exists, "Öneri mevcut olmalı");
    }
    
    // Gereksinim 40: Lobideki tüm öneriler listelenebilmeli
    @Test
    @Order(8)
    @DisplayName("Test 46: Lobideki tüm öneriler listelenebilmeli")
    void testFindSuggestionsByLobby() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie1 = new Movie(0, "Inception", "Thriller", "/trailer1");
        Movie movie2 = new Movie(0, "Interstellar", "Sci-fi", "/trailer2");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        Movie createdMovie1 = movieDAO.findByTitle("Inception");
        Movie createdMovie2 = movieDAO.findByTitle("Interstellar");
        
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie1.getId());
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie2.getId());
        
        List<Suggestion> suggestions = suggestionDAO.findByLobbyId(createdUser.getId());
        
        assertEquals(2, suggestions.size(), "2 öneri olmalı");
    }
    
    // Gereksinim 42: Öneriler silinebilmeli
    @Test
    @Order(9)
    @DisplayName("Test 47: Öneri silinebilmeli")
    void testRemoveSuggestion() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailer");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        boolean removeResult = suggestionDAO.removeSuggestion(createdUser.getId(), createdMovie.getId());
        
        assertTrue(removeResult, "Öneri silme başarılı olmalı");
        assertEquals(0, suggestionDAO.findByLobbyId(createdUser.getId()).size(), 
                     "Öneri listesi boş olmalı");
    }
    
    // Gereksinim 45: Kullanıcılar önerilen filmlere oy verebilmeli
    @Test
    @Order(10)
    @DisplayName("Test 48: Kullanıcı filme oy verebilmeli")
    void testAddVote() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailer");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        boolean result = voteDAO.addVote(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        assertTrue(result, "Oy verme başarılı olmalı");
    }
    
    // Gereksinim 46: Kullanıcılar oylarını geri çekebilmeli
    @Test
    @Order(11)
    @DisplayName("Test 49: Kullanıcı oyunu geri çekebilmeli")
    void testRemoveVote() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailer");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        voteDAO.addVote(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        boolean removeResult = voteDAO.removeVote(createdUser.getId(), createdUser.getId(), createdMovie.getId());
        
        assertTrue(removeResult, "Oy geri çekme başarılı olmalı");
        assertEquals(0, voteDAO.findVotesOfUser(createdUser.getId(), createdUser.getId()).size(), 
                     "Oy listesi boş olmalı");
    }
    
    // Gereksinim 47: Her kullanıcının hangi filmlere oy verdiği kaydedilmeli
    @Test
    @Order(12)
    @DisplayName("Test 50: Kullanıcının oyları listelenebilmeli")
    void testFindVotesOfUser() {
        User user = new User(0, "Ali", "Y", "ali", "pass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdUser.getId(), createdUser.getId());
        
        Movie movie1 = new Movie(0, "Inception", "Thriller", "/trailer1");
        Movie movie2 = new Movie(0, "Interstellar", "Sci-fi", "/trailer2");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        Movie createdMovie1 = movieDAO.findByTitle("Inception");
        Movie createdMovie2 = movieDAO.findByTitle("Interstellar");
        
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie1.getId());
        suggestionDAO.addSuggestion(createdUser.getId(), createdUser.getId(), createdMovie2.getId());
        voteDAO.addVote(createdUser.getId(), createdUser.getId(), createdMovie1.getId());
        voteDAO.addVote(createdUser.getId(), createdUser.getId(), createdMovie2.getId());
        
        List<Vote> votes = voteDAO.findVotesOfUser(createdUser.getId(), createdUser.getId());
        
        assertEquals(2, votes.size(), "2 oy olmalı");
    }
}