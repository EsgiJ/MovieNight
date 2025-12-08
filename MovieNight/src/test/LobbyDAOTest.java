package test;

import models.Lobby;
import models.User;
import dao.LobbyDAO;
import dao.UserDAO;
import org.junit.jupiter.api.*;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LobbyDAO için birim testleri
 * Test edilen gereksinimler: 11-20
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class LobbyDAOTest {
    
    private static Connection connection;
    private LobbyDAO lobbyDAO;
    private UserDAO userDAO;
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:lobbytest;DB_CLOSE_DELAY=-1", "sa", "");
        
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
            """);
        }
    }
    
    @BeforeEach
    void setup() {
        lobbyDAO = new LobbyDAO(connection);
        userDAO = new UserDAO(connection);
    }
    
    @AfterEach
    void cleanup() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM InLobby");
            stmt.execute("DELETE FROM Lobby");
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
    
    // Gereksinim 11: Bir kullanıcı yalnızca bir lobi oluşturabilmeli
    @Test
    @Order(1)
    @DisplayName("Test 16: Kullanıcı başarıyla lobi oluşturabilmeli")
    void testCreateLobbySuccess() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        boolean result = lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        
        assertTrue(result, "Lobi oluşturma başarılı olmalı");
        assertTrue(lobbyDAO.lobbyExists(createdOwner.getId()), "Lobi mevcut olmalı");
    }
    
    // Gereksinim 11: Bir kullanıcı birden fazla lobi oluşturamamalı
    @Test
    @Order(2)
    @DisplayName("Test 17: Kullanıcı ikinci lobi oluşturamamalı")
    void testCreateSecondLobbyFails() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        boolean secondLobby = lobbyDAO.createLobby(createdOwner.getId() + 100, createdOwner.getId());
        
        // İkinci lobi oluşturulsa bile, lobbyExists kontrolü ile kontrol edilmeli
        int lobbyCount = 0;
        try (Statement stmt = connection.createStatement()) {
            ResultSet rs = stmt.executeQuery(
                "SELECT COUNT(*) FROM Lobby WHERE owner_id = " + createdOwner.getId()
            );
            if (rs.next()) {
                lobbyCount = rs.getInt(1);
            }
        } catch (SQLException e) {
            fail("SQL hatası: " + e.getMessage());
        }
        
        // Database constraint veya uygulama mantığı bunu engellemeli
        assertTrue(lobbyCount <= 1, "Kullanıcı en fazla 1 lobiye sahip olmalı");
    }
    
    // Gereksinim 17: Kullanıcının hangi lobiye ait olduğu sorgulanabilmeli
    @Test
    @Order(3)
    @DisplayName("Test 18: Lobi ID'ye göre lobi bulunabilmeli")
    void testFindLobbyById() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        Lobby lobby = lobbyDAO.findById(createdOwner.getId());
        
        assertNotNull(lobby, "Lobi bulunmalı");
        assertEquals(createdOwner.getId(), lobby.getOwnerId(), "Lobi sahibi eşleşmeli");
    }
    
    // Gereksinim 18: Lobinin oylama durumu sorgulanabilmeli
    @Test
    @Order(4)
    @DisplayName("Test 19: Lobi başlangıçta hazır durumda olmamalı")
    void testLobbyInitiallyNotReady() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        Lobby lobby = lobbyDAO.findById(createdOwner.getId());
        
        assertFalse(lobby.isReady(), "Yeni lobi hazır durumda olmamalı");
    }
    
    // Gereksinim 16: Lobi sahibi lobi durumunu "hazır" olarak işaretleyebilmeli
    @Test
    @Order(5)
    @DisplayName("Test 20: Lobi hazır durumuna getirilebilmeli")
    void testSetLobbyReady() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        boolean updateResult = lobbyDAO.setLobbyReady(createdOwner.getId());
        
        assertTrue(updateResult, "Lobi hazır durumuna getirme başarılı olmalı");
        
        Lobby lobby = lobbyDAO.findById(createdOwner.getId());
        assertTrue(lobby.isReady(), "Lobi hazır durumda olmalı");
    }
    
    // Gereksinim 15: Lobi silinebilmeli
    @Test
    @Order(6)
    @DisplayName("Test 21: Lobi başarıyla silinebilmeli")
    void testDeleteLobby() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        boolean deleteResult = lobbyDAO.deleteLobby(createdOwner.getId());
        
        assertTrue(deleteResult, "Lobi silme başarılı olmalı");
        assertFalse(lobbyDAO.lobbyExists(createdOwner.getId()), "Lobi artık mevcut olmamalı");
    }
    
    // Gereksinim 19: Lobi ID'si otomatik oluşturulmalı
    @Test
    @Order(7)
    @DisplayName("Test 22: Lobi ID'si otomatik olarak oluşturulmalı")
    void testLobbyIdAutoGenerated() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        Lobby lobby = lobbyDAO.findById(createdOwner.getId());
        
        assertNotNull(lobby, "Lobi oluşturulmalı");
        assertTrue(lobby.getId() > 0, "Lobi ID'si pozitif olmalı");
    }
    
    // Gereksinim 20: Lobinin oluşturulma tarihi kaydedilmeli
    @Test
    @Order(8)
    @DisplayName("Test 23: Lobi oluşturma tarihi kaydedilmeli")
    void testLobbyDateRecorded() {
        User owner = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        userDAO.createUser(owner);
        User createdOwner = userDAO.findByUsername("ali");
        
        lobbyDAO.createLobby(createdOwner.getId(), createdOwner.getId());
        Lobby lobby = lobbyDAO.findById(createdOwner.getId());
        
        assertNotNull(lobby.getDate(), "Lobi tarihi null olmamalı");
    }
    
    // Gereksinim 17: Lobi varlığı kontrol edilebilmeli
    @Test
    @Order(9)
    @DisplayName("Test 24: Olmayan lobi için lobbyExists false dönmeli")
    void testLobbyExistsReturnsFalse() {
        assertFalse(lobbyDAO.lobbyExists(9999), "Olmayan lobi için false dönmeli");
    }
    
    // Gereksinim 11-20: Tüm lobiler listelenebilmeli
    @Test
    @Order(10)
    @DisplayName("Test 25: Tüm lobiler listelenebilmeli")
    void testFindAllLobbies() {
        User owner1 = new User(0, "Ali", "Yılmaz", "ali", "pass", null);
        User owner2 = new User(0, "Veli", "Demir", "veli", "pass", null);
        userDAO.createUser(owner1);
        userDAO.createUser(owner2);
        User createdOwner1 = userDAO.findByUsername("ali");
        User createdOwner2 = userDAO.findByUsername("veli");
        
        lobbyDAO.createLobby(createdOwner1.getId(), createdOwner1.getId());
        lobbyDAO.createLobby(createdOwner2.getId(), createdOwner2.getId());
        
        var lobbies = lobbyDAO.findAll();
        
        assertEquals(2, lobbies.size(), "2 lobi olmalı");
    }
}