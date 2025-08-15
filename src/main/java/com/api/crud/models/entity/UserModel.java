package com.api.crud.models.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "users")
@ToString
@EqualsAndHashCode
public class UserModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter @Setter
    private long id;

    @Column(name = "firstName")
    @Getter @Setter
    @NotNull (message = "Name cannot be null")
    @Size (min =2, max =25)
    private String firstName;

    @Column(name = "lastName")
    @Getter @Setter
    @NotNull (message = "lastName cannot be null")
    @Size (min =2, max =25)
    private String lastName;

    @Column(name = "email" , unique = true , nullable = false )
    @Getter @Setter
    @NotNull (message = "Email cannot be null")
    @Email (message = "Email should be valid")
    private String email;

    @Column(name= "phone")
    @Getter @Setter
    @Size (min =8, max =20)
    private String phone;

    @Column(name= "password")
    @Getter @Setter
    @NotNull (message = "Password cannot be null")
    private String password;

    @Column(name = "active")
    @Getter @Setter
    private boolean active = true;

    @ManyToMany (fetch = FetchType.LAZY)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Getter @Setter
    private Set<Role> roles = new HashSet<>();

    @OneToMany(mappedBy = "userOrder", cascade = CascadeType.ALL)
    @Getter @Setter
    private List<Order> orders = new ArrayList<>();

    public UserModel(long id, String firstName, String lastName, String email, String phone, String password) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    public UserModel(){}

    public List<String> getRoleNames() {
        if (roles == null) return List.of();
        return roles.stream()
                .map(Role::getNameRole)
                .collect(Collectors.toList());
    }

    public List<String> getOrderIds() {
        if (orders == null) return List.of();
        return orders.stream()
                .map(order -> order.getIdOrder().toString())
                .collect(Collectors.toList());
    }
}