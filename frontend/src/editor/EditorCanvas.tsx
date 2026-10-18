import {
  applyNodeChanges,
  Background,
  Controls,
  MarkerType,
  MiniMap,
  type NodeChange,
  ReactFlow,
  useReactFlow,
} from '@xyflow/react';
import { type MouseEvent, useCallback, useEffect, useMemo, useState } from 'react';

import { PlaybackLayer } from '../playback/PlaybackLayer';
import { fromFlowPosition, moveNode, toFlowPosition } from './model';
import { RoadEdge, type RoadFlowEdge } from './RoadEdge';
import { type RoadFlowNode, RoadNode } from './RoadNode';
import { useEditor } from './store';

const nodeTypes = { road: RoadNode };
const edgeTypes = { road: RoadEdge };
const NODE_SIZE = 28;

/**
 * Lienzo del editor. Los nodos y aristas de React Flow se DERIVAN de la definición del store: React Flow solo
 * guarda su estado local (dimensiones medidas, posición durante el arrastre) y el resultado se confirma en el
 * store al soltar el nodo.
 */
export function EditorCanvas() {
  const definition = useEditor((s) => s.definition);
  const selection = useEditor((s) => s.selection);
  const issues = useEditor((s) => s.issues);
  const connectFrom = useEditor((s) => s.connectFrom);
  const tool = useEditor((s) => s.tool);
  const playback = useEditor((s) => s.playback);
  const { screenToFlowPosition } = useReactFlow();
  // Encuadrar solo al abrir un escenario con contenido. Con uno vacío, React Flow encuadraría al crear el
  // primer nodo y cambiaría el zoom bajo el cursor mientras el usuario dibuja.
  const [fitOnOpen] = useState(() => useEditor.getState().definition.nodes.length > 0);

  const issueIds = useMemo(() => new Set(issues.map((i) => i.elementId).filter(Boolean)), [issues]);
  const signalized = useMemo(() => new Set(definition.signals.map((s) => s.nodeId)), [definition.signals]);

  const derivedNodes = useMemo<RoadFlowNode[]>(() => definition.nodes.map((n) => ({
    id: n.id,
    type: 'road',
    position: toFlowPosition(n),
    width: NODE_SIZE,
    height: NODE_SIZE,
    selected: selection?.kind === 'node' && selection.id === n.id,
    draggable: tool === 'select',
    data: {
      label: n.id,
      nodeType: n.type,
      signalized: signalized.has(n.id),
      hasIssue: issueIds.has(n.id),
      connecting: connectFrom === n.id,
    },
  })), [definition.nodes, selection, tool, signalized, issueIds, connectFrom]);

  const [nodes, setNodes] = useState<RoadFlowNode[]>(derivedNodes);

  // Sincroniza desde el store conservando las dimensiones que React Flow ya midió.
  useEffect(() => {
    setNodes((previous) => {
      const measured = new Map(previous.map((n) => [n.id, n.measured]));
      return derivedNodes.map((n) => ({ ...n, measured: measured.get(n.id) }));
    });
  }, [derivedNodes]);

  const edges = useMemo<RoadFlowEdge[]>(() => definition.edges.map((e) => ({
    id: e.id,
    type: 'road',
    source: e.from,
    target: e.to,
    selected: selection?.kind === 'edge' && selection.id === e.id,
    markerEnd: { type: MarkerType.ArrowClosed, width: 14, height: 14 },
    data: { closed: e.closed, hasIssue: issueIds.has(e.id) },
  })), [definition.edges, selection, issueIds]);

  const onNodesChange = useCallback((changes: NodeChange<RoadFlowNode>[]) => {
    setNodes((current) => applyNodeChanges(changes, current));
    for (const change of changes) {
      if (change.type === 'position' && change.dragging === false && change.position) {
        const { x, y } = fromFlowPosition(change.position);
        useEditor.getState().apply((def) => moveNode(def, change.id, x, y));
      }
    }
  }, []);

  const onPaneClick = useCallback((event: MouseEvent) => {
    const flow = screenToFlowPosition({ x: event.clientX, y: event.clientY });
    const { x, y } = fromFlowPosition(flow);
    useEditor.getState().clickPane(x, y);
  }, [screenToFlowPosition]);

  return (
    <div className={`editor-canvas tool-${tool}`}>
      <ReactFlow<RoadFlowNode, RoadFlowEdge>
        nodes={nodes}
        edges={edges}
        nodeTypes={nodeTypes}
        edgeTypes={edgeTypes}
        nodeOrigin={[0.5, 0.5]}
        onNodesChange={onNodesChange}
        onNodeClick={(_, node) => useEditor.getState().clickNode(node.id)}
        onEdgeClick={(_, edge) => useEditor.getState().select({ kind: 'edge', id: edge.id })}
        onPaneClick={onPaneClick}
        nodesConnectable={false}
        deleteKeyCode={null}
        selectionKeyCode={null}
        multiSelectionKeyCode={null}
        minZoom={0.05}
        maxZoom={4}
        fitView={fitOnOpen}
        fitViewOptions={{ padding: 0.25, maxZoom: 1.5 }}
        defaultViewport={{ x: 0, y: 0, zoom: 1 }}
      >
        <Background gap={50} />
        <MiniMap pannable zoomable />
        <Controls showInteractive={false} />
        {playback && <PlaybackLayer />}
      </ReactFlow>
    </div>
  );
}
