package test;

import models.Movie;
import models.Genre;
import org.junit.jupiter.api.*;

import dao.MovieDAO;
import dao.GenreDAO;
import dao.HasGenreDAO;

import java.sql.*;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MovieDAO için birim testleri
 * Test edilen gereksinimler: 21-30
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MovieDAOTest {
    
    private static Connection connection;
    private MovieDAO movieDAO;
    private GenreDAO genreDAO;
    private HasGenreDAO hasGenreDAO;
    
    @BeforeAll
    static void setupDatabase() throws SQLException {
        connection = DriverManager.getConnection("jdbc:h2:mem:movietest;DB_CLOSE_DELAY=-1", "sa", "");
        
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("""
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
                
                CREATE ALIAS IF NOT EXISTS get_movies_by_all_genres AS $$
                import java.sql.*;
                @CODE
                ResultSet getMoviesByAllGenres(Connection conn, Object[] genreIds) throws SQLException {
                    if (genreIds == null || genreIds.length == 0) {
                        return conn.createStatement().executeQuery("SELECT * FROM Movie WHERE 1=0");
                    }
                    StringBuilder query = new StringBuilder(
                        "SELECT DISTINCT m.* FROM Movie m " +
                        "INNER JOIN HasGenre hg ON m.id = hg.movie_id WHERE hg.genre_id IN ("
                    );
                    for (int i = 0; i < genreIds.length; i++) {
                        if (i > 0) query.append(",");
                        query.append("?");
                    }
                    query.append(")");
                    PreparedStatement stmt = conn.prepareStatement(query.toString());
                    for (int i = 0; i < genreIds.length; i++) {
                        stmt.setInt(i + 1, (Integer) genreIds[i]);
                    }
                    return stmt.executeQuery();
                }
                $$;
            """);
        }
    }
    
    @BeforeEach
    void setup() {
        movieDAO = new MovieDAO(connection);
        genreDAO = new GenreDAO(connection);
        hasGenreDAO = new HasGenreDAO(connection);
    }
    
    @AfterEach
    void cleanup() throws SQLException {
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("DELETE FROM HasGenre");
            stmt.execute("DELETE FROM Movie");
            stmt.execute("DELETE FROM Genre");
        }
    }
    
    @AfterAll
    static void tearDown() throws SQLException {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
    }
    
    // Gereksinim 21: Filmler ID, başlık, açıklama ve fragman yolu ile kaydedilebilmeli
    @Test
    @Order(1)
    @DisplayName("Test 26: Film tüm bilgileri ile kaydedilebilmeli")
    void testCreateMovieWithAllFields() {
        Movie movie = new Movie(0, "Inception", "A mind-bending thriller", "/trailers/inception.mp4");
        
        boolean result = movieDAO.createMovie(movie);
        
        assertTrue(result, "Film oluşturma başarılı olmalı");
    }
    
    // Gereksinim 22: Film başlığına göre arama yapılabilmeli
    @Test
    @Order(2)
    @DisplayName("Test 27: Film başlığına göre arama yapılabilmeli")
    void testFindMovieByTitle() {
        Movie movie = new Movie(0, "Inception", "A mind-bending thriller", "/trailers/inception.mp4");
        movieDAO.createMovie(movie);
        
        Movie foundMovie = movieDAO.findByTitle("Inception");
        
        assertNotNull(foundMovie, "Film bulunmalı");
        assertEquals("Inception", foundMovie.getTitle(), "Film başlığı eşleşmeli");
        assertEquals("A mind-bending thriller", foundMovie.getDescription(), "Açıklama eşleşmeli");
    }
    
    // Gereksinim 22: Olmayan film için null dönmeli
    @Test
    @Order(3)
    @DisplayName("Test 28: Olmayan film için null dönmeli")
    void testFindNonExistentMovieByTitle() {
        Movie foundMovie = movieDAO.findByTitle("NonExistentMovie");
        
        assertNull(foundMovie, "Olmayan film için null dönmeli");
    }
    
    // Gereksinim 23: Tüm filmler listelenebilmeli
    @Test
    @Order(4)
    @DisplayName("Test 29: Tüm filmler listelenebilmeli")
    void testFindAllMovies() {
        Movie movie1 = new Movie(0, "Inception", "Thriller", "/trailers/inception.mp4");
        Movie movie2 = new Movie(0, "The Matrix", "Sci-fi", "/trailers/matrix.mp4");
        Movie movie3 = new Movie(0, "Interstellar", "Space drama", "/trailers/interstellar.mp4");
        
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        movieDAO.createMovie(movie3);
        
        List<Movie> movies = movieDAO.findAll();
        
        assertEquals(3, movies.size(), "3 film olmalı");
    }
    
    // Gereksinim 24: Film ID'sine göre film bilgileri getirilebilmeli
    @Test
    @Order(5)
    @DisplayName("Test 30: Film ID'sine göre film bulunabilmeli")
    void testFindMovieById() {
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailers/inception.mp4");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        Movie foundMovie = movieDAO.findById(createdMovie.getId());
        
        assertNotNull(foundMovie, "Film bulunmalı");
        assertEquals("Inception", foundMovie.getTitle(), "Film başlığı eşleşmeli");
    }
    
    // Gereksinim 25: Filmler birden fazla türe sahip olabilmeli
    @Test
    @Order(6)
    @DisplayName("Test 31: Film birden fazla türe sahip olabilmeli")
    void testMovieWithMultipleGenres() {
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailers/inception.mp4");
        movieDAO.createMovie(movie);
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        Genre action = new Genre(0, "Action");
        Genre thriller = new Genre(0, "Thriller");
        Genre scifi = new Genre(0, "Sci-Fi");
        genreDAO.createGenre(action);
        genreDAO.createGenre(thriller);
        genreDAO.createGenre(scifi);
        
        Genre createdAction = genreDAO.getGenre("Action");
        Genre createdThriller = genreDAO.getGenre("Thriller");
        Genre createdScifi = genreDAO.getGenre("Sci-Fi");
        
        hasGenreDAO.assignGenreToMovie(createdMovie, createdAction);
        hasGenreDAO.assignGenreToMovie(createdMovie, createdThriller);
        hasGenreDAO.assignGenreToMovie(createdMovie, createdScifi);
        
        List<models.HasGenre> movieGenres = hasGenreDAO.getMovieGenres(createdMovie.getId());
        
        assertEquals(3, movieGenres.size(), "Film 3 türe sahip olmalı");
    }
    
    // Gereksinim 26: Belirli türlere sahip filmler filtrelenebilmeli
    @Test
    @Order(7)
    @DisplayName("Test 32: Belirli türdeki filmler filtrelenebilmeli")
    void testFindMoviesByGenre() {
        Movie movie1 = new Movie(0, "Inception", "Thriller", "/trailers/inception.mp4");
        Movie movie2 = new Movie(0, "The Dark Knight", "Action", "/trailers/tdk.mp4");
        movieDAO.createMovie(movie1);
        movieDAO.createMovie(movie2);
        
        Genre action = new Genre(0, "Action");
        genreDAO.createGenre(action);
        Genre createdAction = genreDAO.getGenre("Action");
        
        Movie createdMovie1 = movieDAO.findByTitle("Inception");
        Movie createdMovie2 = movieDAO.findByTitle("The Dark Knight");
        
        hasGenreDAO.assignGenreToMovie(createdMovie2, createdAction);
        
        int[] genreIds = {createdAction.getId()};
        List<Movie> actionMovies = movieDAO.findMoviesByGenres(genreIds);
        
        assertEquals(1, actionMovies.size(), "1 aksiyon filmi olmalı");
        assertEquals("The Dark Knight", actionMovies.get(0).getTitle(), "Film adı eşleşmeli");
    }
    
    // Gereksinim 27: Film açıklamaları görüntülenebilmeli
    @Test
    @Order(8)
    @DisplayName("Test 33: Film açıklaması görüntülenebilmeli")
    void testMovieDescription() {
        String description = "A thief who steals corporate secrets through dream-sharing technology";
        Movie movie = new Movie(0, "Inception", description, "/trailers/inception.mp4");
        movieDAO.createMovie(movie);
        
        Movie foundMovie = movieDAO.findByTitle("Inception");
        
        assertEquals(description, foundMovie.getDescription(), "Açıklama eşleşmeli");
    }
    
    // Gereksinim 28: Film türleri listelenebilmeli
    @Test
    @Order(9)
    @DisplayName("Test 34: Film türleri listelenebilmeli")
    void testListGenres() {
        Genre action = new Genre(0, "Action");
        Genre drama = new Genre(0, "Drama");
        Genre comedy = new Genre(0, "Comedy");
        
        genreDAO.createGenre(action);
        genreDAO.createGenre(drama);
        genreDAO.createGenre(comedy);
        
        List<Genre> genres = genreDAO.findAll();
        
        assertEquals(3, genres.size(), "3 tür olmalı");
    }
    
    // Gereksinim 29: Film ID'si otomatik oluşturulmalı
    @Test
    @Order(10)
    @DisplayName("Test 35: Film ID'si otomatik oluşturulmalı")
    void testMovieIdAutoGenerated() {
        Movie movie = new Movie(0, "Inception", "Thriller", "/trailers/inception.mp4");
        movieDAO.createMovie(movie);
        
        Movie createdMovie = movieDAO.findByTitle("Inception");
        
        assertTrue(createdMovie.getId() > 0, "Film ID'si pozitif olmalı");
    }
    
    // Gereksinim 30: Filmler veritabanına eklenebilmeli
    @Test
    @Order(11)
    @DisplayName("Test 36: Film başarıyla veritabanına eklenebilmeli")
    void testCreateMovieWithID() {
        Movie movie = new Movie(100, "Test Movie", "Description", "/trailer");
        
        boolean result = movieDAO.createMovieWithID(movie);
        
        assertTrue(result, "Film oluşturma başarılı olmalı");
        assertNotNull(movieDAO.findById(100), "Film veritabanında bulunmalı");
    }
    
    // Edge case: Boş başlıklı film
    @Test
    @Order(12)
    @DisplayName("Test 37: Boş başlıklı film oluşturulabilmeli (veritabanı izin veriyorsa)")
    void testCreateMovieWithEmptyTitle() {
        Movie movie = new Movie(0, "", "Description", "/trailer");
        
        boolean result = movieDAO.createMovie(movie);
        
        // Veritabanı kısıtlamasına göre davranış değişebilir
        assertTrue(result, "Film oluşturma başarılı olmalı veya uygun hata vermeli");
    }
    
    // Gereksinim 26: Birden fazla türe göre filtreleme
    @Test
    @Order(13)
    @DisplayName("Test 38: Birden fazla türe göre filmler filtrelenebilmeli")
    void testFindMoviesByMultipleGenres() {
        Movie movie1 = new Movie(0, "Inception", "Thriller", "/trailers/inception.mp4");
        movieDAO.createMovie(movie1);
        
        Genre action = new Genre(0, "Action");
        Genre thriller = new Genre(0, "Thriller");
        genreDAO.createGenre(action);
        genreDAO.createGenre(thriller);
        
        Genre createdAction = genreDAO.getGenre("Action");
        Genre createdThriller = genreDAO.getGenre("Thriller");
        
        Movie createdMovie = movieDAO.findByTitle("Inception");
        hasGenreDAO.assignGenreToMovie(createdMovie, createdAction);
        hasGenreDAO.assignGenreToMovie(createdMovie, createdThriller);
        
        int[] genreIds = {createdAction.getId(), createdThriller.getId()};
        List<Movie> movies = movieDAO.findMoviesByGenres(genreIds);
        
        assertTrue(movies.size() > 0, "En az bir film bulunmalı");
    }
}