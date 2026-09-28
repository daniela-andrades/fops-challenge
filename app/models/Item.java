package models;

import javax.persistence.Entity;
import play.db.jpa.Model;

@Entity
public class Item extends Model {

  public String name;

  public Item(String name) {
    this.name = name;
  }
}
