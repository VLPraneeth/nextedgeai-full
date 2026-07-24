package com.syncari.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@Accessors(chain = true)
@ToString
public class IdMapping extends UUIDAuditModel {
    //map of system name vs id
    private String entityName;
    private String syncariId;
    private List<Mapping> mappings= new ArrayList<>();

    public boolean isMapped(String externalEntityDefinitionId) {
        return mappings.stream().anyMatch(m->m.isConnected() && m.getEntityDefinitionId().equalsIgnoreCase(externalEntityDefinitionId));
    }

    public boolean hasConnectedMappings(){
        return !getConnectedMappings().isEmpty();
    }
    public List<Mapping> getConnectedMappings(){
        return getMappings().stream().filter(m->m.isConnected()).collect(Collectors.toList());
    }

    public boolean isMapped(String synapseId,String externalEntityDefinitionId) {
        return findBy(synapseId, externalEntityDefinitionId).anyMatch(m->m.isConnected());
    }
    public boolean isDisconnected(String synapseId,String externalEntityDefinitionId) {
        var mappings = findBy(synapseId, externalEntityDefinitionId).collect(Collectors.toList());
        return !mappings.isEmpty() && mappings.stream().allMatch(m->m.isDisconnected());
    }

    public IdMapping upsertMappings(IdMapping incoming) {
        incoming.getMappings().forEach(mapping->{
            final Optional<Mapping> existingMapping = findBy(mapping.getConnectorId(), mapping.getEntityDefinitionId(), mapping.getEntityId());
            existingMapping.ifPresentOrElse(
                    //updated flag using incoming mapping
                    e->e.setDisconnected(mapping.isDisconnected()),
                    //OR add as new mapping
                    () -> addMapping(mapping)
            );
        });
        return this;
    }

    public boolean hasDisconnectedRecord(List<String> externalEntityDefinitionIds) {
        return mappings.stream().anyMatch(m->m.isDisconnected() && externalEntityDefinitionIds.contains(m.getEntityDefinitionId()));
    }

    public List<IdMapping.Mapping> getDisconnectedMappings(List<String> externalEntityDefinitionIds) {
        return mappings.stream().filter(m->m.isDisconnected() && externalEntityDefinitionIds.contains(m.getEntityDefinitionId())).collect(Collectors.toList());
    }

    @Data @AllArgsConstructor public static class Mapping {
        private String connectorId;
        private String entityId;
        private String entityDefinitionId;
        private String originalSyncariId;
        private boolean disconnected;
        private Mapping(){

        }

        public boolean isConnected(){
            return !disconnected;
        }

    }

    public Optional<Mapping> getMapping(String synapseId, String externalEntityDefinitionId){
        return findBy(synapseId, externalEntityDefinitionId).filter(m ->m.isConnected()).findFirst();
    }
    public Optional<Mapping> getMapping(String externalEntityDefinitionId){
        return  mappings.stream().filter(m -> externalEntityDefinitionId.equalsIgnoreCase(m.entityDefinitionId) && m.isConnected()).findFirst();
    }

    public Optional<Mapping> findMapping(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        return findBy(synapseId, externalEntityDefinitionId, externalRecordId).filter(m->m.isConnected());
    }

    /*
    Find mapping either connected/disconnected
     */
    public Optional<Mapping> findAllMapping(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        return findBy(synapseId, externalEntityDefinitionId, externalRecordId);
    }
    public Optional<Mapping> findDisconnected(String externalEntityDefinitionId){
        List<Mapping> allMappings = mappings.stream().filter(m -> externalEntityDefinitionId.equalsIgnoreCase(m.entityDefinitionId) && m.isDisconnected())
                .collect(Collectors.toList());
        if(allMappings.size() == 1 && allMappings.get(0).isDisconnected()) return Optional.of(allMappings.get(0));
        return  Optional.empty();
    }

    public Optional<Mapping> findDisconnected(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        return findBy(synapseId, externalEntityDefinitionId, externalRecordId).filter(m->m.isDisconnected());
    }

    protected Optional<Mapping> findBy(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        return  findBy(synapseId, externalEntityDefinitionId)
                .filter(m ->externalRecordId.equalsIgnoreCase(m.entityId))
                .findFirst();
    }
    protected Stream<Mapping> findBy(String synapseId, String externalEntityDefinitionId){
        return  mappings.stream().filter(m ->
                m.connectorId.equals(synapseId)
                        && externalEntityDefinitionId.equalsIgnoreCase(m.entityDefinitionId)
        );
    }

    public IdMapping removeMapping(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        Optional<Mapping> mapping = findBy(synapseId, externalEntityDefinitionId, externalRecordId);
        mapping.ifPresent(m->this.mappings.remove(m));
        return this;
    }
    public IdMapping disconnectMapping(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        Optional<Mapping> mapping = findBy(synapseId, externalEntityDefinitionId, externalRecordId);
        mapping.ifPresent(m->m.setDisconnected(true));
        return this;
    }
    public IdMapping reconnectMapping(String synapseId, String externalEntityDefinitionId, String externalRecordId){
        Optional<Mapping> mapping = findBy(synapseId, externalEntityDefinitionId, externalRecordId);
        mapping.ifPresent(m->m.setDisconnected(false));
        return this;
    }

    public List<Mapping> getMappings(String synapseId, String externalEntityDefinitionId){
        return findBy(synapseId, externalEntityDefinitionId)
                .filter(m ->m.isConnected())
                .collect(Collectors.toList());
    }
    public List<Mapping> getAllMappings(String synapseId, String externalEntityDefinitionId){
        return findBy(synapseId, externalEntityDefinitionId)
                .collect(Collectors.toList());
    }

    public Optional<Mapping> getMapping(String synapseId, String externalEntityDefinitionId, String originalSyncariId){
        return findBy(synapseId, externalEntityDefinitionId).filter(
                m ->m.isConnected()
                        && (Objects.equals(originalSyncariId,m.originalSyncariId))
        ).findFirst();
    }

    public IdMapping addMapping(String connectorId, String id, String entityDefinitionId){
        return upsertMapping(mapping(connectorId, id, entityDefinitionId));
    }

    public IdMapping addMapping(String connectorId, String id, String entityDefinitionId, boolean isDisconnected){
        return upsertMapping(mapping(connectorId, id, entityDefinitionId, isDisconnected));
    }

    private IdMapping upsertMapping(Mapping mapping) {
        final Optional<Mapping> existing = findBy(mapping.getConnectorId(), mapping.getEntityDefinitionId(), mapping.getEntityId());
        existing.ifPresentOrElse(
                //set disconnect flag
                e ->e.setDisconnected(mapping.isDisconnected()),
                //OR add a new mapping
                ()->this.mappings.add(mapping)
                //If there was a deleted mapping for a different record, but the same synapse/entity?
                //R1 comes from SFDC and wass attached to S1. Later R1 is deleted in SFDC, so its idmapping is marked deleted
                // Still later, R2 comes from SFDC and qualifies to be attached to S1.
        );
        return this;
    }

    public IdMapping addMapping(IdMapping.Mapping mapping){
        upsertMapping(mapping);
        return this;
    }
    public IdMapping addMapping(String connectorId, String id, String entityDefinitionId,String originalSyncariId){
        upsertMapping(mapping(connectorId, id, entityDefinitionId, originalSyncariId));
        return this;
    }

    public static Mapping mapping(String connectorId, String id, String entityDefinitionId){
        return new Mapping(connectorId, id,entityDefinitionId, null,false);
    }
    public static Mapping mapping(String connectorId, String id, String entityDefinitionId, String originalSyncariId){
        return new Mapping(connectorId, id,entityDefinitionId, originalSyncariId,false);
    }

    public static Mapping mapping(String connectorId, String id, String entityDefinitionId, boolean isDisconnected){
        return new Mapping(connectorId, id,entityDefinitionId, null, isDisconnected);
    }
}


