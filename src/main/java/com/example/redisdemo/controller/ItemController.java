package com.example.redisdemo.controller;

import com.example.redisdemo.model.Item;
import com.example.redisdemo.repository.ItemRepository;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemRepository itemRepository;
    private final RedisConnectionFactory connectionFactory;

    public ItemController(ItemRepository itemRepository, RedisConnectionFactory connectionFactory) {
        this.itemRepository = itemRepository;
        this.connectionFactory = connectionFactory;
    }

    // ---- CRUD Endpoints ----

    /**
     * CREATE - Add a new item.
     * POST /api/items
     * Body: { "name": "test-key", "description": "some value" }
     */
    @PostMapping
    public ResponseEntity<Item> create(@RequestBody Item item) {
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        Item saved = itemRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    @GetMapping(path = "/create")
    public ResponseEntity<Item> putWithGet(@RequestParam Integer id, @RequestParam String name, @RequestParam String desc) {
        Item item = new Item();
        item.setCreatedAt(Instant.now());
        item.setUpdatedAt(Instant.now());
        item.setName(name);
        item.setDescription(desc);
        item.setId(String.valueOf(id));
        Item saved = itemRepository.save(item);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    /**
     * READ - Get a single item by ID.
     * GET /api/items/{id}
     */
    @GetMapping("/{id}")
    public ResponseEntity<Item> getById(@PathVariable String id) {
        return itemRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * READ - Search items by name.
     * GET /api/items/search?name=test-key
     */
    @GetMapping("/search")
    public ResponseEntity<List<Item>> searchByName(@RequestParam String name) {
        List<Item> items = itemRepository.findByName(name);
        return ResponseEntity.ok(items);
    }

    /**
     * UPDATE - Update an existing item.
     * PUT /api/items/{id}
     * Body: { "name": "updated-key", "description": "updated value" }
     */
    @PutMapping("/{id}")
    public ResponseEntity<Item> update(@PathVariable String id, @RequestBody Item item) {
        return itemRepository.findById(id)
                .map(existing -> {
                    existing.setName(item.getName());
                    existing.setDescription(item.getDescription());
                    existing.setUpdatedAt(Instant.now());
                    Item saved = itemRepository.save(existing);
                    return ResponseEntity.ok(saved);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE - Remove an item by ID.
     * DELETE /api/items/{id}
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        if (itemRepository.existsById(id)) {
            itemRepository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }

    // ---- Get All ----

    /**
     * LIST ALL - Retrieve every item in the store.
     * GET /api/items
     */
    @GetMapping
    public ResponseEntity<List<Item>> getAll() {
        List<Item> items = new ArrayList<>();
        itemRepository.findAll().forEach(items::add);
        return ResponseEntity.ok(items);
    }

    // ---- Diagnostics ----
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> connectionInfo() {
        Map<String, Object> info = new LinkedHashMap<>();
        try {
            var connection = connectionFactory.getConnection();
            var serverInfo = connection.serverCommands().info("server");

            info.put("status", "CONNECTED");
            info.put("connectionFactory", connectionFactory.getClass().getSimpleName());

            // Parse out key server details for migration validation
            if (serverInfo != null) {
                info.put("redis_version", serverInfo.getProperty("redis_version", "unknown"));
                info.put("redis_mode", serverInfo.getProperty("redis_mode", "unknown"));
                info.put("os", serverInfo.getProperty("os", "unknown"));
                info.put("tcp_port", serverInfo.getProperty("tcp_port", "unknown"));
                info.put("uptime_in_seconds", serverInfo.getProperty("uptime_in_seconds", "unknown"));
                info.put("server_name", serverInfo.getProperty("server_name", "redis"));
            }

            // Item count for quick sanity check
            info.put("item_count", itemRepository.count());

            connection.close();
        } catch (Exception e) {
            info.put("status", "ERROR");
            info.put("error", e.getMessage());
        }
        return ResponseEntity.ok(info);
    }
}
