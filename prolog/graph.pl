:- module(graph, 
	  [ 'contains*'/3,
	    name_from_id/3,
	    full_name_from_id/3,
	    can_contain/2, 	 

	    %% graph_impl interface
	    find_graph/1,
	    get_node/3, %get a node from a known id
	    gen_node/3,%select randomly a node
	    contains/3,
	    uses/3,
	    
	    get_roots/2,
	    root/2,
	    leaf/2,
	    
	    %graph setters
	    put_node/3,
	    put_uses/4,
	    put_contains/4,
	    
	    % "extracter" behave as lists:select but with elements of the graph
	    %% select_node/4, Not implemented in graph_assoc
	    select_uses/4,
	    select_contains/4,

	    abstract_node/6, %exposed for javaRules and sigmaRules, the solver should use abstract/6
	    
	    %node getters
	    name_of_node/2,
	    type_of_node/2,
	    id_of_node/2,
	    ids_to_use/3]).


%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%%%%%  language specific rules %%%%%%%%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

:- use_module(javaRules).
:- reexport(javaRules,
%% :- use_module(sigmaRules).
%% :- reexport(sigmaRules, 
	    [abstract/6,
	     abstract_type/2]).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%%%%%%%%        graph impl         %%%%%%%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%% :-reexport(graph_list2dot).
%% :- ensure_loaded(graph_list).

:- reexport(graph_assoc2dot).
:- ensure_loaded(graph_assoc).

%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%
%%% generic higher level operations %%%%%%%%%
%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%%

get_roots(Ids, G):-
    findall(Id, (is_root(N), gen_node(Id,N,G)), Ids).

can_contain(Cer, Cee):- 
    type_of_node(CerType, Cer), type_of_node(CeeType,Cee),
    can_contain_type(CerType,CeeType).

name_from_id(NodeId,NameSig, Graph):- 
    get_node(NodeId, Node, Graph), name_of_node(NameSig, Node).

'contains*'(X,Z, Graph) :- contains(Y,Z,Graph), 'contains*'(X,Y, Graph).
'contains*'(X,X, Graph) :- get_node(X, _, Graph).

%%%%%%%%%%%%%%%%

full_name_from_id(NodeId,FullName, Graph):- 
		path_to_node(NodeId,Path, Graph),
		names_on_path(Path, Names, Graph),
		atomic_list_concat(Names,'.',FullName).

%%%%%%%%%%%%

names_on_path(NodesIds, Names, Graph):- 
    names_on_path(NodesIds, [], RevNames, Graph), reverse(RevNames, Names).

names_on_path([],Acc,Acc, _).
names_on_path([NodeId|NodesIds],Acc, Names,Graph):- 
    name_from_id(NodeId, Name, Graph),
    names_on_path(NodesIds, [Name | Acc], Names, Graph).

% Path to Node through scopes (Ids only)
path_to_node(Node,Path, Graph):- path(Node,[],Path, Graph).
		
path(Top,Path,[Top|Path], Graph):- root(Top, Graph).%,!.
path(NodeId,Path,Result, Graph):-
    contains(OwnerId, NodeId, Graph),
    path(OwnerId,[NodeId|Path], Result, Graph).


