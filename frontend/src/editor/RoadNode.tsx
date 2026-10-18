import { Handle, type Node, type NodeProps, Position } from '@xyflow/react';

import type { NodeType } from '../api/types';
import { NODE_TYPE_LABEL } from '../ui/labels';

export type RoadNodeData = {
  label: string;
  nodeType: NodeType;
  signalized: boolean;
  hasIssue: boolean;
  connecting: boolean;
};

export type RoadFlowNode = Node<RoadNodeData, 'road'>;

/** Handles invisibles: React Flow los necesita para dibujar aristas, pero conectamos con la herramienta "Conectar". */
const hiddenHandle = { opacity: 0, top: '50%', left: '50%', pointerEvents: 'none' } as const;

export function RoadNode({ data, selected }: NodeProps<RoadFlowNode>) {
  const classes = [
    'road-node',
    `road-node--${data.nodeType.toLowerCase()}`,
    selected ? 'is-selected' : '',
    data.hasIssue ? 'has-issue' : '',
    data.connecting ? 'is-connecting' : '',
  ].join(' ');

  return (
    <div className={classes} title={`${NODE_TYPE_LABEL[data.nodeType]} ${data.label}`}>
      {data.signalized && <span className="signal-badge" aria-label="Semáforo">●</span>}
      <span className="road-node__label">{data.label}</span>
      <Handle type="target" position={Position.Top} style={hiddenHandle} isConnectable={false} />
      <Handle type="source" position={Position.Top} style={hiddenHandle} isConnectable={false} />
    </div>
  );
}
