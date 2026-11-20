package org.springframework.samples.petclinic.rest.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.samples.petclinic.mapper.OwnerMapper;
import org.springframework.samples.petclinic.mapper.PetMapper;
import org.springframework.samples.petclinic.rest.dto.OwnerDto;
import org.springframework.samples.petclinic.rest.dto.PetDto;
import org.springframework.samples.petclinic.rest.dto.PetTypeDto;
import org.springframework.samples.petclinic.service.ClinicService;
import org.springframework.samples.petclinic.service.clinicService.ApplicationTestConfig;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@ContextConfiguration(classes = ApplicationTestConfig.class)
@WebAppConfiguration
public class V2RestControllersTests {
    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private OwnerMapper ownerMapper;

    @Autowired
    private PetMapper petMapper;

    @MockitoBean
    private ClinicService clinicService;

    private MockMvc mockMvc;

    private List<OwnerDto> owners;

    private List<PetDto> pets;

    @BeforeEach
    void initOwners() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        owners = new ArrayList<>();

        OwnerDto ownerWithPet = new OwnerDto();
        owners.add(ownerWithPet.id(1).firstName("George").lastName("Franklin").address("110 W. Liberty St.").city("Madison").telephone("6085551023"));
        OwnerDto owner = new OwnerDto();
        owners.add(owner.id(2).firstName("Betty").lastName("Davis").address("638 Cardinal Ave.").city("Sun Prairie").telephone("6085551749"));
        owner = new OwnerDto();
        owners.add(owner.id(3).firstName("Eduardo").lastName("Rodriquez").address("2693 Commerce St.").city("McFarland").telephone("6085558763"));
        owner = new OwnerDto();
        owners.add(owner.id(4).firstName("Harold").lastName("Davis").address("563 Friendly St.").city("Windsor").telephone("6085553198"));

        PetTypeDto petType = new PetTypeDto();
        petType.id(2)
            .name("dog");

        pets = new ArrayList<>();
        PetDto pet = new PetDto();
        pets.add(pet.id(3)
            .name("Rosy")
            .birthDate(LocalDate.now())
            .type(petType));

        pet = new PetDto();
        pets.add(pet.id(4)
            .name("Jewel")
            .birthDate(LocalDate.now())
            .type(petType));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageSuccess() throws Exception {
        var pageRequest = PageRequest.of(0, 2, Sort.by("id"));
        var pageOwners = ownerMapper.toOwners(owners.subList(0, 2)).stream().toList();
        given(this.clinicService.findOwners(null, pageRequest))
            .willReturn(new PageImpl<>(pageOwners, pageRequest, owners.size()));
        this.mockMvc.perform(get("/api/v2/owners?page=0&size=2")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.content[0].id").value(1))
            .andExpect(jsonPath("$.content[0].firstName").value("George"))
            .andExpect(jsonPath("$.content[1].id").value(2))
            .andExpect(jsonPath("$.content[1].firstName").value("Betty"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(2))
            .andExpect(jsonPath("$.totalElements").value(4))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageSuccess() throws Exception {
        var pageRequest = PageRequest.of(0, 5, Sort.by("id"));
        var pagePets = petMapper.toPets(pets).stream().toList();
        given(this.clinicService.findPets(pageRequest))
            .willReturn(new PageImpl<>(pagePets, pageRequest, pets.size()));
        this.mockMvc.perform(get("/api/v2/pets?page=0&size=5")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(content().contentType("application/json"))
            .andExpect(jsonPath("$.content[0].id").value(3))
            .andExpect(jsonPath("$.content[0].name").value("Rosy"))
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(5))
            .andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageDefaults() throws Exception {
        var pageRequest = PageRequest.of(0, 20, Sort.by("id"));
        given(this.clinicService.findOwners(null, pageRequest))
            .willReturn(new PageImpl<>(ownerMapper.toOwners(owners).stream().toList(), pageRequest, owners.size()));
        this.mockMvc.perform(get("/api/v2/owners")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageDefaults() throws Exception {
        var pageRequest = PageRequest.of(0, 20, Sort.by("id"));
        given(this.clinicService.findPets(pageRequest))
            .willReturn(new PageImpl<>(petMapper.toPets(pets).stream().toList(), pageRequest, pets.size()));
        this.mockMvc.perform(get("/api/v2/pets")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(0))
            .andExpect(jsonPath("$.size").value(20))
            .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageSizeMin() throws Exception {
        expectOwnersPage(0, 1);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageSizeMin() throws Exception {
        expectPetsPage(0, 1);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageSizeMax() throws Exception {
        expectOwnersPage(0, 100);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageSizeMax() throws Exception {
        expectPetsPage(0, 100);
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageNegativePage() throws Exception {
        expectBadRequest("/api/v2/owners?page=-1&size=20");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageNegativePage() throws Exception {
        expectBadRequest("/api/v2/pets?page=-1&size=20");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageSizeZero() throws Exception {
        expectBadRequest("/api/v2/owners?page=0&size=0");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageSizeZero() throws Exception {
        expectBadRequest("/api/v2/pets?page=0&size=0");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageSizeTooLarge() throws Exception {
        expectBadRequest("/api/v2/owners?page=0&size=101");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageSizeTooLarge() throws Exception {
        expectBadRequest("/api/v2/pets?page=0&size=101");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetOwnersPageNonInteger() throws Exception {
        expectBadRequest("/api/v2/owners?page=abc&size=20");
    }

    @Test
    @WithMockUser(roles = "OWNER_ADMIN")
    void testGetPetsPageNonInteger() throws Exception {
        expectBadRequest("/api/v2/pets?page=abc&size=20");
    }

    @Test
    @WithMockUser(roles = "VET_ADMIN")
    void testGetOwnersPageWrongRole() throws Exception {
        this.mockMvc.perform(get("/api/v2/owners")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "VET_ADMIN")
    void testGetPetsPageWrongRole() throws Exception {
        this.mockMvc.perform(get("/api/v2/pets")
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden());
    }

    private void expectOwnersPage(int page, int size) throws Exception {
        var pageRequest = PageRequest.of(page, size, Sort.by("id"));
        given(this.clinicService.findOwners(null, pageRequest))
            .willReturn(new PageImpl<>(List.of(), pageRequest, owners.size()));
        this.mockMvc.perform(get("/api/v2/owners?page=" + page + "&size=" + size)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(page))
            .andExpect(jsonPath("$.size").value(size));
    }

    private void expectPetsPage(int page, int size) throws Exception {
        var pageRequest = PageRequest.of(page, size, Sort.by("id"));
        given(this.clinicService.findPets(pageRequest))
            .willReturn(new PageImpl<>(List.of(), pageRequest, pets.size()));
        this.mockMvc.perform(get("/api/v2/pets?page=" + page + "&size=" + size)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.page").value(page))
            .andExpect(jsonPath("$.size").value(size));
    }

    private void expectBadRequest(String url) throws Exception {
        this.mockMvc.perform(get(url)
                .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.schemaValidationErrors").isArray())
            .andExpect(jsonPath("$.schemaValidationErrors").isNotEmpty());
    }
}
