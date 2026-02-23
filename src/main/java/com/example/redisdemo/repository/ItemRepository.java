package com.example.redisdemo.repository;

import com.example.redisdemo.model.Item;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRepository extends CrudRepository<Item, String> {

    List<Item> findByName(String name);
}
