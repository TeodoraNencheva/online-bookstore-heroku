package bg.softuni.onlinebookstore.model.entity;

import javax.persistence.*;
import java.util.*;

@Entity
@Table(name = "users")
public class UserEntity extends BaseEntity {

    @Column(nullable = false)
    private String firstName;

    @Column(nullable = false)
    private String lastName;

    @Column(nullable = false, unique = true)
    private String email;

    private String password;

    private boolean accountVerified;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "users_roles",
            joinColumns = @JoinColumn(name = "user_id", referencedColumnName = "id"),
            inverseJoinColumns = @JoinColumn(name = "role_id", referencedColumnName = "id"))
    private Set<UserRoleEntity> roles;

    @ElementCollection
    @CollectionTable(name = "carts_items",
            joinColumns = {@JoinColumn(name = "user_id", referencedColumnName = "id")})
    @MapKeyJoinColumn(name = "book_id")
    @Column(name = "quantity")
    private Map<BookEntity, Integer> cart;

    public UserEntity() {
    }

    public UserEntity(String firstName, String lastName, String email, String password, Set<UserRoleEntity> roles) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.password = password;
        this.roles = roles;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public boolean isAccountVerified() {
        return accountVerified;
    }

    public void setAccountVerified(boolean accountVerified) {
        this.accountVerified = accountVerified;
    }

    public Set<UserRoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(Set<UserRoleEntity> roles) {
        this.roles = roles;
    }

    public Map<BookEntity, Integer> getCart() {
        return cart;
    }

    public void setCart(Map<BookEntity, Integer> cart) {
        this.cart = cart;
    }

    public void addRole(UserRoleEntity role) {
        if (this.roles == null) {
            this.roles = new HashSet<>();
        }
        this.roles.add(role);
    }

    public void addToCart(BookEntity book, int quantity) {
        if (this.cart == null || this.cart.size() == 0) {
            cart = new LinkedHashMap<>();
        }

        cart.merge(book, quantity, Integer::sum);
    }

    public void removeFromCart(BookEntity book) {
        cart.remove(book);
    }

    public void emptyCart() {
        cart.clear();
    }

    public String getFullName() {
        return firstName + " " + lastName;
    }
}
