package models;

import javax.persistence.Entity;
import play.db.jpa.Model;

@Entity
public class User extends Model {

  public String name;
  public String email;

  public User(String name, String email) {
    this.name = name;
    this.email = email;
  }
}
