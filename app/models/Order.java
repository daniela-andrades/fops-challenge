package models;

import java.util.Date;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import play.db.jpa.Model;

@Entity
@Table(name = "ItemOrder")
public class Order extends Model {

  public Date creationDate;

  @ManyToOne
  public Item item;

  public Integer quantity;

  @ManyToOne
  public User user;

  public Order(Date creationDate, Item item, Integer quantity, User user) {
    this.creationDate = creationDate;
    this.item = item;
    this.quantity = quantity;
    this.user = user;
  }
}
