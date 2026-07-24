package com.syncari.api.core.util;

import com.syncari.api.rest.controllers.data.fragment.FragmentDTO;
import com.syncari.api.rest.controllers.data.fragment.FragmentEdgeDTO;
import com.syncari.api.rest.controllers.data.fragment.FragmentGraphDTO;
import com.syncari.api.rest.controllers.data.fragment.FragmentNodeDTO;
import com.syncari.connector.exception.NotSupportedException;
import com.syncari.core.SyncariContext;
import com.syncari.core.datatype.DatatypeFactory;
import com.syncari.core.datatype.ObjectType;
import com.syncari.core.exception.NotFoundException;
import com.syncari.core.model.ActionDefinition;
import com.syncari.core.model.AttributeDefinition;
import com.syncari.core.model.misc.fragment.CoreAttributeFragmentNodeConfig;
import com.syncari.core.model.misc.fragment.CoreEntityFragmentNodeConfig;
import com.syncari.core.model.EntityDefinition;
import com.syncari.core.model.FunctionCall;
import com.syncari.core.model.FunctionDefinition;
import com.syncari.core.model.GenericActionConfig;
import com.syncari.core.model.InputPort;
import com.syncari.core.model.OutputPort;
import com.syncari.core.model.SimpleFunctionNodeConfig;
import com.syncari.core.model.UserPreference;
import com.syncari.core.model.Fragment;
import com.syncari.core.model.misc.fragment.FragmentEdge;
import com.syncari.core.model.misc.fragment.FragmentGraph;
import com.syncari.core.model.Layout;
import com.syncari.core.model.NodeConfiguration;
import com.syncari.core.model.Tag;
import com.syncari.core.model.User;
import com.syncari.core.model.misc.fragment.FragmentNode;
import com.syncari.core.model.misc.fragment.FragmentSharePreference;
import com.syncari.core.model.misc.Taggable;
import com.syncari.core.model.util.Scope;
import com.syncari.core.repositories.customer.AttributeRepo;
import com.syncari.core.repositories.customer.EntityDefinitionRepo;
import com.syncari.core.service.*;
import com.syncari.core.utils.ValidationUtils;
import com.syncari.restutils.data.NodeRef;
import com.syncari.restutils.data.PortDTO;
import com.syncari.restutils.transformers.GraphTransformer;
import com.syncari.restutils.utils.NodeConfigMapVisitor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.syncari.utils.I18n.i18n;

@Component
public class FragmentTransformer {

    @Autowired
    UserService userService;

    @Autowired
    TagService tagService;

    @Autowired
    GraphTransformer graphTransformer;

    @Autowired
    AttributeRepo attributeProxyRepo;

    @Autowired
    EntityDefinitionRepo entityProxyRepo;

    @Autowired
    ActionService actionDefinitionRepo;

    @Autowired
    FunctionService functionService;

    @Autowired
    FragmentService fragmentService;

    private static final String FRAGMENT_ICON_PATH = "/assets/icons/fragment.svg";
    private static final String SHARED_FRAGMENT_ICON_PATH = "/assets/icons/shared-fragment.svg";

    public Fragment toFragment(FragmentDTO fragmentDTO){
        Fragment fragment = new Fragment();
        fragment.setId(fragmentDTO.getId());
        fragment.setName(fragmentDTO.getDisplayName());
        fragment.setDescription(fragmentDTO.getDescription());
        fragment.setScope(fragmentDTO.getScope());

        User owner = userService.getUser(SyncariContext.getUser().getEmail());
        fragment.setOwnerUserId(owner.getId());
        fragment.setFragmentGraph(toFragmentGraph(fragmentDTO.getFragment(), fragmentDTO.getScope()));
        var tags = fragmentDTO.getTags().stream()
                .map(t -> new Tag(t, true, Taggable.fragment, fragmentDTO.getId()))
                .collect(Collectors.toList());
        fragment.setTags(tags);

        return fragment;
    }

    private FragmentGraph toFragmentGraph(FragmentGraphDTO graphDTO, Scope scope){
        FragmentGraph graph = new FragmentGraph();
        List<FragmentNode> nodes = graphDTO == null
                ? Collections.emptyList()
                : graphDTO.getNodes().stream().map(n -> toFragmentNode(n, scope)).collect(Collectors.toList());
        graph.setNodes(nodes);

        List<FragmentEdge> edges =graphDTO == null
                ? Collections.emptyList()
                : graphDTO.getEdges().stream().map(edge -> toFragmentEdge(edge)).collect(Collectors.toList());
        graph.setEdges(edges);
        graph.setLayouts(extractLayout(graphDTO));
        return graph;
    }

    private FragmentNode toFragmentNode(FragmentNodeDTO nodeDTO, Scope scope){
        NodeConfiguration nodeConfiguration = toNodeConfiguration(nodeDTO);
        FragmentNode node = new FragmentNode();
        node.setName(nodeDTO.getName());
        node.setApiName(nodeDTO.getApiName());
        node.setScope(scope);
        node.setConfiguration(nodeConfiguration);
        node.setTemplateId(nodeDTO.getId());
        return node;
    }

    private FragmentEdge toFragmentEdge(FragmentEdgeDTO edgeDTO) {
        var inputDatatype = DatatypeFactory.getDatatype(edgeDTO.getDestination().getPort().getDatatype());
        var outputDatatype = DatatypeFactory.getDatatype(edgeDTO.getSource().getPort().getDatatype());
        FragmentEdge edge = new FragmentEdge();
        edge.setInput(new InputPort(inputDatatype, edgeDTO.getDestination().getPort().getMaxConnections()));
        edge.setOutput(new OutputPort(outputDatatype, edgeDTO.getSource().getPort().getMaxConnections()));
        var sourceStage = new FragmentNode();
        sourceStage.setTemplateId(edgeDTO.getSource().getNodeId());
        var destStage = new FragmentNode();
        destStage.setTemplateId(edgeDTO.getDestination().getNodeId());
        edge.setDestinationStage(destStage);
        edge.setSourceStage(sourceStage);
        edge.setTemplateId(edgeDTO.getId());
        return edge;
    }

    private List<Layout> extractLayout(FragmentGraphDTO graphDTO) {
        Stream<Layout> edgelayouts = graphDTO.getEdges().stream().map(edge -> Layout.edge(edge.getId(), edge.getSource().getAnchor(), edge.getDestination().getAnchor()));
        Stream<Layout> nodelayouts = graphDTO.getNodes().stream().map(node -> {
            var location = node.getLocation();
            return Layout.node(node.getId(),
                    location.containsKey("x") ? location.get("x").toString() :
                            Layout.isCoreType(node.getNodeType()) ? Layout.DEFAULT_CENTER_X : String.valueOf(Layout.cappedRandom()),
                    location.containsKey("y") ? location.get("y").toString() :
                            Layout.isCoreType(node.getNodeType()) ? Layout.DEFAULT_CENTER_Y : String.valueOf(Layout.cappedRandom()));
        });
        List<Layout> layouts = edgelayouts.collect(Collectors.toList());
        layouts.addAll(nodelayouts.collect(Collectors.toList()));
        return layouts;
    }

    // TODO: can be removed
    public List<FragmentDTO> toFragmentDTOs(List<Fragment> fragments){
        return fragments.stream().map(f -> toFragmentDTO(f)).collect(Collectors.toList());
    }

    public FragmentDTO toFragmentDTO(Fragment fragment){
        FragmentDTO dto = new FragmentDTO();
        dto.setId(fragment.getId());
        dto.setDisplayName(fragment.getName());
        dto.setDescription(fragment.getDescription());
        dto.setScope(fragment.getScope());

        User owner = userService.findUserById(fragment.getOwnerUserId())
                .orElse(new User().setEmail("UNKNOWN").setFirstName("UNKNOWN").setLastName(""));
        dto.setOwnerEmail(owner.getEmail());
        dto.setOwnerFirstName(owner.getFirstName());
        dto.setOwnerLastName(owner.getLastName());
        dto.setTags(tagService.getTagNames(Taggable.fragment, fragment.getId()));
        dto.setShared(fragment.isShared());
        dto.setIconPath(fragment.isShared() ? SHARED_FRAGMENT_ICON_PATH : FRAGMENT_ICON_PATH);
        dto.setFragment(toFragmentGraphDTO(fragment.getFragmentGraph()));
        UserPreference preference = userService.getPreference(SyncariContext.getUser().getId());
        FragmentSharePreference fragmentPref = preference.getFragmentShare();
        if(fragmentPref != null) {
            dto.setHidden(fragmentPref.getHidden().contains(fragment.getId()));
        }
        if(!fragment.isShared()){
            Set<String> sharingInstances = fragmentService.getSharingInstances(fragment.getId());
            dto.setSharedWithInstances(!sharingInstances.isEmpty());
        }
        return dto;
    }

    private FragmentGraphDTO toFragmentGraphDTO(FragmentGraph graph){
        FragmentGraphDTO graphDTO = new FragmentGraphDTO();

        Map<String, Layout> idToNodeLayoutMapping = graph.getLayouts().stream().filter(l -> Layout.NODE_TYPE.equals(l.getTargetType()))
                .collect(Collectors.toMap(Layout::getTargetId, l -> l));
        Map<String, Layout> idToEdgeLayoutMapping = graph.getLayouts().stream().filter(l -> Layout.EDGE_TYPE.equals(l.getTargetType()))
                .collect(Collectors.toMap(Layout::getTargetId, l -> l));

        graphDTO.setNodes(graph.getNodes().stream().map(n -> toFragmentNodeDTO(n, idToNodeLayoutMapping.get(n.getTemplateId())))
                .collect(Collectors.toList()));
        graphDTO.setEdges(graph.getEdges().stream().map(e -> toFragmentEdgeDTO(e, idToEdgeLayoutMapping.get(e.getTemplateId())))
                .collect(Collectors.toList()));

        return graphDTO;
    }

    private FragmentNodeDTO toFragmentNodeDTO(FragmentNode node, Layout layout) {
        if(layout == null){
            layout = Layout.node(node.getId(), "0", "0");
        }
        var nodeConfigVisitor = new NodeConfigMapVisitor();
        node.getConfiguration().accept(nodeConfigVisitor);
        FragmentNodeDTO fragmentNode = new FragmentNodeDTO();
        fragmentNode.setInputPorts(graphTransformer.toInputPortDTO(node.getConfiguration().getInputPorts()));
        fragmentNode.setOutputPorts(graphTransformer.toOutputPortDTO(node.getConfiguration().getOutputPorts()));
        fragmentNode.setName(node.getName());
        fragmentNode.setApiName(node.getApiName());
        fragmentNode.setLabel(node.getName());
        fragmentNode.setSubLabel(graphTransformer.generateSubLabel(node));
        fragmentNode.setNodeType(node.getConfiguration().getNodeType());
        fragmentNode.setConfiguration(node.getConfiguration().getConfigMap());
        fragmentNode.setTemplateId(node.getTemplateId());
        fragmentNode.setLocation(layout.getLayoutProperties());
        return fragmentNode;
    }

    private FragmentEdgeDTO toFragmentEdgeDTO(FragmentEdge edge, Layout layout) {
        if(layout == null){
            layout = Layout.edge(edge.getTemplateId(), "0", "0");
        }
        FragmentEdgeDTO fragmentEdgeDTO = new FragmentEdgeDTO();
        fragmentEdgeDTO.setSource(new NodeRef(edge.getSourceStage().getTemplateId(), PortDTO.fromOutputPort(edge.getOutput()),
                layout.getLayoutProperties().get("srcAnchor").toString()));
        fragmentEdgeDTO.setDestination(new NodeRef(edge.getDestinationStage().getTemplateId(), PortDTO.fromInputPort(edge.getInput()),
                layout.getLayoutProperties().get("destAnchor").toString()));
        fragmentEdgeDTO.setTemplateId(edge.getTemplateId());

        return fragmentEdgeDTO;
    }

    private NodeConfiguration toNodeConfiguration(FragmentNodeDTO nodeDTO) {

        NodeConfiguration nodeConfiguration = null;
        ValidationUtils.validateCondition(nodeDTO.getNodeType()==null,i18n("missing_direction"),nodeDTO.getLabel());
        switch (nodeDTO.getNodeType()) {
            case CORE_ENTITY: {
                // set a placeholder entity in node configuration
                var entity = new EntityDefinition(nodeDTO.getApiName(), nodeDTO.getName());
                // TODO: v2 - copy entire config and resolve at destination
                nodeConfiguration = new CoreEntityFragmentNodeConfig().setEntityDefinition(entity);
                        //.setDedupeConfig(graphTransformer.getDedupeConfig(nodeDTO))
                        //.setAdvancedDedupeConfig(graphTransformer.getAdvancedDedupeConfig(nodeDTO).orElse(null))
                        //.setDataAuthority(graphTransformer.getDataAuthority(nodeDTO));
            }
            break;
            case FUNCTION:
                FunctionDefinition functionDefinition = functionService.findById(nodeDTO.getRequiredConfiguration("definition").toString()).orElseThrow();
                var functionCall = new FunctionCall().setFunctionDefinition(functionDefinition);
                Map<String, Object> configuration = nodeDTO.getConfiguration();
                functionCall.setConfig(configuration);
                nodeConfiguration = new SimpleFunctionNodeConfig().setFunctionCall(functionCall);
                break;
            case PREDICATE:
                break;
            case CORE_ATTRIBUTE: {
                // set a placeholder attribute in node configuration
                var attribute = new AttributeDefinition().setApiName(nodeDTO.getApiName()).setDataType(new ObjectType());
                nodeConfiguration = new CoreAttributeFragmentNodeConfig().setAttributeDefinition(attribute);
            }
            break;
            case ACTION:
                ActionDefinition actionDefinition = actionDefinitionRepo.findByName(nodeDTO.getApiName())
                        .orElseThrow(() -> new NotFoundException(ActionDefinition.class, "name", nodeDTO.getApiName()));
                nodeConfiguration = new GenericActionConfig().setConfigMap(nodeDTO.getConfiguration())
                        .setName(actionDefinition.getName()).setActionDefinition(actionDefinition);
                break;
            case ATTRIBUTE_SINK:
                throw new NotSupportedException(String.format(i18n("fragment_node_not_supported_error"), "Attribute Sink"));
            case ATTRIBUTE_SOURCE:
                throw new NotSupportedException(String.format(i18n("fragment_node_not_supported_error"), "Attribute Source"));
            case ENTITY_SINK:
                throw new NotSupportedException(String.format(i18n("fragment_node_not_supported_error"), "Entity Sink"));
            case ENTITY_SOURCE:
                throw new NotSupportedException(String.format(i18n("fragment_node_not_supported_error"), "Entity Source"));

            default:
                throw new IllegalStateException("Unexpected value: " + nodeDTO.getNodeType());
        }
        return nodeConfiguration;
    }
}
