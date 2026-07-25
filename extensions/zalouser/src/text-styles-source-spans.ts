import type { MarkdownIR } from "openclaw/plugin-sdk/text-chunking";

export function sourceContainerPrefixLength(
  line: string,
  lineIndex: number,
  ir: MarkdownIR,
  sourceLineStarts: number[],
  sourceLines: string[],
  blockquoteDepth: number,
): number {
  return sourceContainerProjection(
    line,
    lineIndex,
    ir,
    sourceLineStarts,
    sourceLines,
    blockquoteDepth,
  ).offset;
}

export function sourceContainerProjection(
  line: string,
  lineIndex: number,
  ir: MarkdownIR,
  sourceLineStarts: number[],
  sourceLines: string[],
  blockquoteDepth: number,
): { offset: number; residual: number } {
  const quotePrefix = sourceBlockquotePrefixLength(line, blockquoteDepth);
  const quoteResidual = blockquoteTabResidual(line.slice(0, quotePrefix));
  const listProjection = (ir.listItems ?? []).reduce(
    (projection, item) => {
      if (
        item.sourceContent === undefined ||
        item.sourceStartLine === undefined ||
        item.sourceEndLine === undefined ||
        lineIndex < item.sourceStartLine ||
        lineIndex >= item.sourceEndLine
      ) {
        return projection;
      }
      const itemColumn = item.sourceContent.start - (sourceLineStarts[item.sourceStartLine] ?? 0);
      if (lineIndex === item.sourceStartLine) {
        return itemColumn > projection.offset ? { offset: itemColumn, residual: 0 } : projection;
      }
      const itemQuotePrefix = sourceBlockquotePrefixLength(
        sourceLines[item.sourceStartLine] ?? "",
        blockquoteDepth,
      );
      const itemLineStart = sourceLineStarts[item.sourceStartLine] ?? 0;
      const markerEndColumn = item.sourceMarker?.end
        ? item.sourceMarker.end - itemLineStart
        : itemColumn;
      const quoteColumn = markdownSourceColumn(
        (sourceLines[item.sourceStartLine] ?? "").slice(0, itemQuotePrefix),
      );
      const listIndent =
        item.sourceIndent !== undefined
          ? item.sourceIndent - quoteColumn
          : markdownSourceColumn(
              (sourceLines[item.sourceStartLine] ?? "").slice(
                itemQuotePrefix,
                item.markerOnly ? markerEndColumn : itemColumn,
              ),
              quoteColumn,
            ) -
            quoteColumn +
            (item.markerOnly ? 1 : 0);
      const consumed = consumeMarkdownIndentProjection(
        line.slice(quotePrefix),
        listIndent,
        markdownSourceColumn(line.slice(0, quotePrefix)),
      );
      const offset = quotePrefix + consumed.offset;
      return offset > projection.offset ? { offset, residual: consumed.residual } : projection;
    },
    { offset: 0, residual: 0 },
  );
  const consumedQuoteDepth = line.slice(0, listProjection.offset).match(/>/gu)?.length ?? 0;
  const trailingQuotePrefix = sourceBlockquotePrefixLength(
    line.slice(listProjection.offset),
    Math.max(0, blockquoteDepth - consumedQuoteDepth),
  );
  const trailingQuoteResidual = blockquoteTabResidual(
    line.slice(listProjection.offset, listProjection.offset + trailingQuotePrefix),
    markdownSourceColumn(line.slice(0, listProjection.offset)),
  );
  const offset = Math.max(listProjection.offset + trailingQuotePrefix, quotePrefix);
  const residual =
    listProjection.offset > 0 && trailingQuotePrefix > 0
      ? trailingQuoteResidual
      : listProjection.offset > 0
        ? listProjection.residual
        : quoteResidual;
  return {
    offset,
    residual,
  };
}

export function sourceBlockquotePrefixLength(
  line: string,
  maxDepth = Number.POSITIVE_INFINITY,
): number {
  let cursor = 0;
  let depth = 0;
  while (cursor < line.length && depth < maxDepth) {
    let marker = cursor;
    while (marker < line.length && marker - cursor < 3 && line[marker] === " ") {
      marker += 1;
    }
    if (line[marker] !== ">") {
      break;
    }
    cursor = marker + 1;
    depth += 1;
    if (line[cursor] === " " || line[cursor] === "\t") {
      cursor += 1;
    }
  }
  return cursor;
}

export function markdownSourceColumn(text: string, initialColumn = 0): number {
  let column = initialColumn;
  for (const character of text) {
    column += character === "\t" ? 4 - (column % 4) : 1;
  }
  return column;
}

export function blockquoteTabResidual(prefix: string, initialColumn = 0): number {
  let column = initialColumn;
  let residual = 0;
  for (let index = 0; index < prefix.length; index += 1) {
    const character = prefix[index];
    if (character !== "\t") {
      if (character === ">") {
        residual = 0;
      }
      column += 1;
      continue;
    }
    const width = 4 - (column % 4);
    if (prefix[index - 1] === ">") {
      residual = Math.max(0, width - 1);
    }
    column += width;
  }
  return residual;
}

function consumeMarkdownIndentProjection(
  text: string,
  requiredColumns: number,
  initialColumn = 0,
): { offset: number; residual: number } {
  let column = initialColumn;
  let consumedColumns = 0;
  let cursor = 0;
  while (
    cursor < text.length &&
    consumedColumns < requiredColumns &&
    /[ \t]/u.test(text[cursor] ?? "")
  ) {
    const width = text[cursor] === "\t" ? 4 - (column % 4) : 1;
    column += width;
    consumedColumns += width;
    cursor += 1;
  }
  return { offset: cursor, residual: Math.max(0, consumedColumns - requiredColumns) };
}
