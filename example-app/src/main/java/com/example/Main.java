package com.example;

import com.example.model.User;
import com.example.model.UserDto;
// Generated classes will be available after compilation:
// import com.example.model.UserBuilder;                  // Generated
// import com.example.model.UserToUserDtoMapper;         // Generated

public class Main {
    public static void main(String[] args) {
        // Use generated builder
        com.example.model.UserBuilder userBuilder = new com.example.model.UserBuilder()
                .id(1)
                .name("Alice")
                .email("alice@example.com");
        User user = userBuilder.build();

        // Use generated mapper
        com.example.model.UserToUserDtoMapper mapperRef = null; // not needed, static methods
        UserDto dto = com.example.model.UserToUserDtoMapper.map(user);

        System.out.println("User => " + user.getName() + ", " + user.getEmail());
        System.out.println("DTO  => " + dto.getName() + ", " + dto.getEmail());
    }
}