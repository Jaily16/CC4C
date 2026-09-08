/**
 * 为两端 Vue/JavaScript 提供中文功能边界注释检查，不依赖额外 ESLint 插件。
 *
 * 局部集合回调、Promise 链和懒加载组件由所属语义函数统一说明，不要求逐行翻译式注释。
 */

const CHINESE_PATTERN = /[\u3400-\u9fff]/u;
const BOUNDARY_CALLEES = new Set([
  'onBeforeMount',
  'onBeforeUnmount',
  'onMounted',
  'onUnmounted',
  'onUpdated',
  'watch',
  'watchEffect',
  'setInterval',
  'setTimeout',
]);
const ROUTER_BOUNDARIES = new Set(['beforeEach', 'afterEach', 'onError']);
const EVENT_BOUNDARIES = new Set(['addEventListener', 'removeEventListener']);
const LOCAL_CALLBACKS = new Set([
  'map',
  'filter',
  'reduce',
  'find',
  'findIndex',
  'some',
  'every',
  'forEach',
  'sort',
  'then',
  'catch',
  'finally',
]);

/** 返回成员表达式最末端的属性名。 */
function memberName(node) {
  if (!node) return null;
  if (node.type === 'Identifier') return node.name;
  if (node.type === 'MemberExpression' && !node.computed && node.property.type === 'Identifier') {
    return node.property.name;
  }
  if (node.type === 'MemberExpression' && node.computed && node.property.type === 'Literal') {
    return String(node.property.value);
  }
  return null;
}

/** 将声明提升到 export 包装层，确保注释可以自然写在导出语句之前。 */
function commentAnchor(node) {
  let anchor = node;
  if (anchor.type === 'VariableDeclarator') anchor = anchor.parent;
  if (anchor.type === 'CallExpression') {
    while (
      anchor.parent &&
      [
        'AwaitExpression',
        'AssignmentExpression',
        'ExpressionStatement',
        'IfStatement',
        'ReturnStatement',
        'VariableDeclarator',
        'VariableDeclaration',
      ].includes(anchor.parent.type)
    ) {
      anchor = anchor.parent;
    }
  }
  if (anchor.parent?.type === 'ExportNamedDeclaration' || anchor.parent?.type === 'ExportDefaultDeclaration') {
    anchor = anchor.parent;
  }
  return anchor;
}

/** 判断节点之前是否紧邻一条包含中文的说明性注释。 */
function hasAdjacentChineseComment(sourceCode, node) {
  const anchor = commentAnchor(node);
  const comments = sourceCode.getCommentsBefore(anchor);
  const nearest = comments.at(-1);
  return Boolean(
    nearest &&
      CHINESE_PATTERN.test(nearest.value) &&
      nearest.loc.end.line >= anchor.loc.start.line - 2,
  );
}

/** 尽可能提取函数或边界调用的稳定名称，便于错误定位。 */
function displayName(node) {
  if (node.type === 'FunctionDeclaration') return node.id?.name ?? '<匿名函数>';
  if (node.type === 'VariableDeclarator') return node.id.type === 'Identifier' ? node.id.name : '<解构函数>';
  if (node.type === 'CallExpression') return memberName(node.callee) ?? '<边界回调>';
  return '<功能边界>';
}

/** 判断调用是否属于生命周期、监听、路由、定时器或拦截器边界。 */
function isBoundaryCall(node) {
  const name = memberName(node.callee);
  if (BOUNDARY_CALLEES.has(name)) return true;
  if (node.callee.type !== 'MemberExpression') return false;
  if (ROUTER_BOUNDARIES.has(name) || EVENT_BOUNDARIES.has(name)) return true;
  if (name === 'use') {
    const objectText = node.callee.object;
    return objectText.type === 'MemberExpression' && memberName(objectText) === 'response'
      ? memberName(objectText.object) === 'interceptors'
      : objectText.type === 'MemberExpression' && memberName(objectText) === 'request'
        ? memberName(objectText.object) === 'interceptors'
        : false;
  }
  return false;
}

/** 判断变量是否承载 computed 或 defineStore 等派生业务边界。 */
function isSemanticCallVariable(node) {
  if (node.type !== 'VariableDeclarator' || node.init?.type !== 'CallExpression') return false;
  const name = memberName(node.init.callee);
  return name === 'computed' || name === 'defineStore';
}

/** 创建同时适用于普通 JS 与 Vue SFC 的中文功能注释规则。 */
function createRule(context) {
  const sourceCode = context.sourceCode;
  const reported = new Set();

  /** 对同一源码锚点只报告一次缺失说明。 */
  function requireComment(node) {
    const anchor = commentAnchor(node);
    const key = `${anchor.range?.[0] ?? anchor.loc.start.line}:${displayName(node)}`;
    if (reported.has(key) || hasAdjacentChineseComment(sourceCode, node)) return;
    reported.add(key);
    context.report({
      node: anchor,
      message: `功能边界“${displayName(node)}”前缺少紧邻的中文职责、状态或副作用说明。`,
    });
  }

  return {
    /** 每个 Vue 组件必须在脚本入口声明中文职责。 */
    Program(node) {
      if (!context.filename.endsWith('.vue')) return;
      const commentBlocks =
        sourceCode.text.match(/<!--[\s\S]*?-->|\/\*[\s\S]*?\*\/|\/\/[^\r\n]*/gu) ?? [];
      const hasResponsibility = commentBlocks.some((comment) => CHINESE_PATTERN.test(comment));
      if (!hasResponsibility) {
        context.report({ node, message: 'Vue 组件脚本入口缺少中文组件职责说明。' });
      }
    },

    /** 具名函数声明必须说明其功能边界。 */
    FunctionDeclaration(node) {
      requireComment(node);
    },

    /** 具名函数变量以及 computed/defineStore 派生边界必须有中文说明。 */
    VariableDeclarator(node) {
      if (
        node.init?.type === 'ArrowFunctionExpression' ||
        node.init?.type === 'FunctionExpression' ||
        isSemanticCallVariable(node)
      ) {
        requireComment(node);
      }
    },

    /** 生命周期、监听、路由、定时器、事件绑定和 Axios 拦截器调用必须说明目的与清理责任。 */
    CallExpression(node) {
      const name = memberName(node.callee);
      if (LOCAL_CALLBACKS.has(name)) return;
      if (isBoundaryCall(node)) requireComment(node);
    },
  };
}

export default {
  rules: {
    'require-chinese-functional-comment': {
      meta: {
        type: 'suggestion',
        docs: { description: '要求 Vue/JavaScript 功能边界使用紧邻的中文说明' },
        schema: [],
      },
      create: createRule,
    },
  },
};
