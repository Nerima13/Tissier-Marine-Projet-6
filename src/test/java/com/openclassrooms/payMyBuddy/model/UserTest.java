package com.openclassrooms.payMyBuddy.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class UserTest {

    @Test
    public void addConnection() {
        User laure = new User();
        laure.setUsername("Laure");

        User clara = new User();
        clara.setUsername("Clara");

        laure.addConnection(clara);

        assertTrue(laure.getConnections().contains(clara), "Laure must contain Clara in connections");
        assertTrue(clara.getConnected().contains(laure), "Clara must contain Laure in connected (reverse side)");
    }

    @Test
    public void addConnection_ignoresSelfAndDuplicateConnections() {
        User laure = new User();
        laure.setUsername("Laure");

        // Auto-login ignored
        laure.addConnection(laure);
        assertTrue(laure.getConnections().isEmpty(), "A user must not log in to himself");

        // Duplicate ignored
        User clara = new User();
        clara.setUsername("Clara");

        laure.addConnection(clara);
        laure.addConnection(clara); // duplicate
        assertEquals(1, laure.getConnections().size(), "No duplicates should be added");
    }

    @Test
    void removeConnection() {
        User laure = new User();
        User clara = new User();

        laure.addConnection(clara);
        assertTrue(laure.getConnections().contains(clara));
        assertTrue(clara.getConnected().contains(laure));

        laure.removeConnection(clara);

        assertFalse(laure.getConnections().contains(clara), "The connection must be removed on the Laure side");
        assertFalse(clara.getConnected().contains(laure), "The connection must be removed on the Clara side");
    }
}
