package test;

import models.User;
import dao.UserDAO;
import org.junit.jupiter.api.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

/**
 * UserDAO için birim testleri
 * Test edilen gereksinimler: 1-10
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UserDAOTest {
    
    private static Connection connection;
    private UserDAO userDAO;
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        // H2 in-memory database kullanarak test
        connection = DriverManager.getConnection("jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1", "sa", "");
        
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
                
                CREATE OR REPLACE VIEW user_identifiers AS
                SELECT id, username FROM "User";
                
                CREATE ALIAS IF NOT EXISTS get_user_by_credentials AS $$
                import java.sql.*;
                @CODE
                ResultSet getUserByCredentials(Connection conn, String username, String password) throws SQLException {
                    PreparedStatement stmt = conn.prepareStatement(
                        "SELECT id, fname, lname, username, age, created_at FROM \\"User\\" WHERE username = ? AND password = ?"
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
        userDAO = new UserDAO(connection);
    }
    
    @AfterEach
    void cleanup() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
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
    
    // Gereksinim 1: Benzersiz kullanıcı adı ile kayıt
    @Test
    @Order(1)
    @DisplayName("Test 1: Benzersiz kullanıcı adı ile kayıt başarılı olmalı")
    void testCreateUserWithUniqueUsername() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        
        boolean result = userDAO.createUser(user);
        
        assertTrue(result, "Kullanıcı oluşturma başarılı olmalı");
        assertNotNull(userDAO.findByUsername("aliyilmaz"), "Kullanıcı veritabanında bulunmalı");
    }
    
    // Gereksinim 1 ve 5: Aynı kullanıcı adı ile tekrar kayıt olunamamalı
    @Test
    @Order(2)
    @DisplayName("Test 2: Duplicate kullanıcı adı ile kayıt başarısız olmalı")
    void testCreateUserWithDuplicateUsername() {
        User user1 = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        User user2 = new User(0, "Veli", "Demir", "aliyilmaz", "pass456", null);
        
        userDAO.createUser(user1);
        boolean result = userDAO.createUser(user2);
        
        assertFalse(result, "Aynı kullanıcı adı ile ikinci kayıt başarısız olmalı");
    }
    
    // Gereksinim 4: Kullanıcı adının boş olmaması kontrolü
    @Test
    @Order(3)
    @DisplayName("Test 3: Boş kullanıcı adı ile kayıt başarısız olmalı")
    void testCreateUserWithBlankUsername() {
        User user = new User(0, "Ali", "Yılmaz", "", "pass123", null);
        
        boolean result = userDAO.createUser(user);
        
        assertFalse(result, "Boş kullanıcı adı ile kayıt başarısız olmalı");
    }
    
    // Gereksinim 2: Şifrenin boş olmaması kontrolü
    @Test
    @Order(4)
    @DisplayName("Test 4: Boş şifre ile kayıt başarısız olmalı")
    void testCreateUserWithBlankPassword() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "", null);
        
        boolean result = userDAO.createUser(user);
        
        assertFalse(result, "Boş şifre ile kayıt başarısız olmalı");
    }
    
    // Gereksinim 6: Doğru kullanıcı adı ve şifre ile giriş
    @Test
    @Order(5)
    @DisplayName("Test 5: Doğru kullanıcı adı ve şifre ile giriş başarılı olmalı")
    void testGetUserByCredentialsSuccess() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        userDAO.createUser(user);
        
        boolean result = userDAO.getUserByCredentials("aliyilmaz", "pass123");
        
        assertTrue(result, "Doğru kimlik bilgileri ile giriş başarılı olmalı");
    }
    
    // Gereksinim 6: Yanlış şifre ile giriş başarısız olmalı
    @Test
    @Order(6)
    @DisplayName("Test 6: Yanlış şifre ile giriş başarısız olmalı")
    void testGetUserByCredentialsWrongPassword() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        userDAO.createUser(user);
        
        boolean result = userDAO.getUserByCredentials("aliyilmaz", "wrongpass");
        
        assertFalse(result, "Yanlış şifre ile giriş başarısız olmalı");
    }
    
    // Gereksinim 6: Olmayan kullanıcı ile giriş başarısız olmalı
    @Test
    @Order(7)
    @DisplayName("Test 7: Olmayan kullanıcı ile giriş başarısız olmalı")
    void testGetUserByCredentialsNonExistentUser() {
        boolean result = userDAO.getUserByCredentials("nonexistent", "pass123");
        
        assertFalse(result, "Olmayan kullanıcı ile giriş başarısız olmalı");
    }
    
    // Gereksinim 7: Kullanıcı şifresi güncellenebilmeli
    @Test
    @Order(8)
    @DisplayName("Test 8: Kullanıcı şifresi başarıyla güncellenebilmeli")
    void testUpdateUserPassword() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "oldpass", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("aliyilmaz");
        
        boolean updateResult = userDAO.updateUserPassword(createdUser.getId(), "newpass");
        
        assertTrue(updateResult, "Şifre güncelleme başarılı olmalı");
        assertTrue(userDAO.getUserByCredentials("aliyilmaz", "newpass"), 
                   "Yeni şifre ile giriş yapılabilmeli");
        assertFalse(userDAO.getUserByCredentials("aliyilmaz", "oldpass"), 
                    "Eski şifre ile giriş yapılamamalı");
    }
    
    // Gereksinim 8: Kullanıcı bilgileri (ad, soyad) güncellenebilmeli
    @Test
    @Order(9)
    @DisplayName("Test 9: Kullanıcı bilgileri başarıyla güncellenebilmeli")
    void testUpdateUserDetails() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("aliyilmaz");
        
        boolean updateResult = userDAO.updateUserDetails(createdUser.getId(), "Mehmet", "Demir");
        
        assertTrue(updateResult, "Kullanıcı bilgileri güncelleme başarılı olmalı");
        
        User updatedUser = userDAO.findById(createdUser.getId());
        assertEquals("Mehmet", updatedUser.getFname(), "İsim güncellenmiş olmalı");
        assertEquals("Demir", updatedUser.getLname(), "Soyisim güncellenmiş olmalı");
    }
    
    // Gereksinim 9: Kullanıcılar silinebilmeli
    @Test
    @Order(10)
    @DisplayName("Test 10: Kullanıcı başarıyla silinebilmeli")
    void testDeleteUser() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("aliyilmaz");
        
        boolean deleteResult = userDAO.deleteById(createdUser.getId());
        
        assertTrue(deleteResult, "Kullanıcı silme işlemi başarılı olmalı");
        assertNull(userDAO.findById(createdUser.getId()), "Silinen kullanıcı bulunamaz olmalı");
    }
    
    // Gereksinim 10: Tüm kullanıcı adları listelenebilmeli
    @Test
    @Order(11)
    @DisplayName("Test 11: Tüm kullanıcı adları listelenebilmeli")
    void testFindAllUsername() {
        User user1 = new User(0, "Ali", "Yılmaz", "ali", "pass1", null);
        User user2 = new User(0, "Veli", "Demir", "veli", "pass2", null);
        User user3 = new User(0, "Ayşe", "Kaya", "ayse", "pass3", null);
        
        userDAO.createUser(user1);
        userDAO.createUser(user2);
        userDAO.createUser(user3);
        
        var usernames = userDAO.findAllUsername();
        
        assertEquals(3, usernames.size(), "3 kullanıcı adı olmalı");
        assertTrue(usernames.contains("ali"), "ali kullanıcı adı listede olmalı");
        assertTrue(usernames.contains("veli"), "veli kullanıcı adı listede olmalı");
        assertTrue(usernames.contains("ayse"), "ayse kullanıcı adı listede olmalı");
    }
    
    // Gereksinim 10: Kullanıcı ID'ye göre bulunabilmeli
    @Test
    @Order(12)
    @DisplayName("Test 12: Kullanıcı ID'ye göre bulunabilmeli")
    void testFindById() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        userDAO.createUser(user);
        User createdUser = userDAO.findByUsername("aliyilmaz");
        
        User foundUser = userDAO.findById(createdUser.getId());
        
        assertNotNull(foundUser, "Kullanıcı bulunmalı");
        assertEquals("aliyilmaz", foundUser.getUsername(), "Kullanıcı adı eşleşmeli");
        assertEquals("Ali", foundUser.getFname(), "İsim eşleşmeli");
    }
    
    // Gereksinim 10: Kullanıcı adına göre bulunabilmeli
    @Test
    @Order(13)
    @DisplayName("Test 13: Kullanıcı adına göre bulunabilmeli")
    void testFindByUsername() {
        User user = new User(0, "Ali", "Yılmaz", "aliyilmaz", "pass123", null);
        userDAO.createUser(user);
        
        User foundUser = userDAO.findByUsername("aliyilmaz");
        
        assertNotNull(foundUser, "Kullanıcı bulunmalı");
        assertEquals("Ali", foundUser.getFname(), "İsim eşleşmeli");
        assertEquals("Yılmaz", foundUser.getLname(), "Soyisim eşleşmeli");
    }
    
    // Gereksinim 10: Tüm kullanıcılar listelenebilmeli
    @Test
    @Order(14)
    @DisplayName("Test 14: Tüm kullanıcılar listelenebilmeli")
    void testFindAll() {
        User user1 = new User(0, "Ali", "Yılmaz", "ali", "pass1", null);
        User user2 = new User(0, "Veli", "Demir", "veli", "pass2", null);
        
        userDAO.createUser(user1);
        userDAO.createUser(user2);
        
        var users = userDAO.findAll();
        
        assertEquals(2, users.size(), "2 kullanıcı olmalı");
    }
    
    // Edge case: Null kullanıcı ile oluşturma
    @Test
    @Order(15)
    @DisplayName("Test 15: Null kullanıcı ile oluşturma başarısız olmalı")
    void testCreateUserWithNull() {
        assertThrows(NullPointerException.class, () -> {
            userDAO.createUser(null);
        }, "Null kullanıcı ile exception fırlatılmalı");
    }
}