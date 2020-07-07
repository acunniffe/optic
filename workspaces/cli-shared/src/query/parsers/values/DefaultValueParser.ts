import { IQueryStringValueParser } from '../../query-parser-interfaces';

class DefaultValueParser implements IQueryStringValueParser {
  parse(key: string, values: string[]): any {
    if (values.length === 1) {
      return values[0];
    } else {
      return values[values.length - 1];
    }
  }

  shouldHandle(key: string, values: string[]): boolean {
    return true;
  }
}

export const DefaultValueParserInstance = new DefaultValueParser();
