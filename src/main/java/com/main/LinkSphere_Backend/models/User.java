package com.main.LinkSphere_Backend.models;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Data
@Table(name="users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;
    private String email;
    @Column(unique = true)
    private String username;
    private String password;
    private String role="ROLE_USER";
}
