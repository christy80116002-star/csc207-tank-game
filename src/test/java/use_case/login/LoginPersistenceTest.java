package use_case.login;

import data_access.FileUserDataAccessObject;
import data_access.InMemoryCurrentPlayerDataAccessObject;
import entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginPersistenceTest {
    @TempDir
    Path directory;

    private Path accountsFile;
    private InMemoryCurrentPlayerDataAccessObject players;
    private RecordingPresenter presenter;
    private LoginInteractor interactor;

    @BeforeEach
    void setUp() {
        accountsFile = directory.resolve("accounts.properties");
        players = new InMemoryCurrentPlayerDataAccessObject();
        presenter = new RecordingPresenter();
        interactor = new LoginInteractor(new FileUserDataAccessObject(accountsFile), players, presenter);
    }

    @Test
    void savedAccountCanBeUsedAfterRestart() throws Exception {
        interactor.signUp(input("correct-password", 1));
        assertEquals("alice", players.findBySide(1).orElseThrow().getUsername());
        assertFalse(Files.readString(accountsFile).contains("correct-password"));

        InMemoryCurrentPlayerDataAccessObject restartedPlayers =
                new InMemoryCurrentPlayerDataAccessObject();
        LoginInteractor restarted = new LoginInteractor(
                new FileUserDataAccessObject(accountsFile), restartedPlayers, presenter);

        restarted.execute(input("correct-password", 2));

        Player alice = restartedPlayers.findBySide(2).orElseThrow();
        assertEquals("alice", alice.getUsername());
        assertFalse(alice.isGuest());
        assertTrue(restartedPlayers.findBySide(1).isEmpty());
    }

    @Test
    void wrongPasswordLeavesCurrentPlayerIntact() {
        interactor.signUp(input("correct-password", 2));
        Player existing = players.findBySide(2).orElseThrow();

        interactor.execute(input("wrong-password", 2));

        assertEquals("Incorrect username or password.", presenter.message);
        assertSame(existing, players.findBySide(2).orElseThrow());
    }

    @ParameterizedTest
    @CsvSource({"' ',password", "alice,''"})
    void emptyCredentialsAreRejected(String username, String password) {
        LoginInputData input = new LoginInputData(username, password.toCharArray(), 1);
        interactor.execute(input);
        assertEquals("Enter a username and password.", presenter.message);

        interactor.signUp(input);
        assertEquals("Enter a username and password.", presenter.message);
        assertTrue(players.findBySide(1).isEmpty());
        assertFalse(Files.exists(accountsFile));
    }

    @Test
    void guestSelectionOnlyChangesTheRequestedSide() {
        interactor.signUp(input("correct-password", 1));
        Player existing = players.findBySide(1).orElseThrow();

        interactor.continueAsGuest(2);

        Player guest = players.findBySide(2).orElseThrow();
        assertTrue(guest.isGuest());
        assertEquals("Guest 2", guest.getUsername());
        assertSame(existing, players.findBySide(1).orElseThrow());
        assertNull(presenter.message);
    }

    @Test
    void duplicateSignupLeavesCurrentPlayerIntact() {
        interactor.signUp(input("correct-password", 1));
        interactor.continueAsGuest(2);
        Player guest = players.findBySide(2).orElseThrow();

        interactor.signUp(input("another-password", 2));

        assertEquals("That username is already taken.", presenter.message);
        assertSame(guest, players.findBySide(2).orElseThrow());
    }

    private LoginInputData input(String password, int side) {
        return new LoginInputData("alice", password.toCharArray(), side);
    }

    private static final class RecordingPresenter implements LoginOutputBoundary {
        private String message;

        @Override
        public void prepareSuccess(String username, int side) {
            message = null;
        }

        @Override
        public void prepareFailure(String message) {
            this.message = message;
        }
    }
}
