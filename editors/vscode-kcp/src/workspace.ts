/**
 * ワークスペース内の `.kcp` ファイルインデックスです。
 *
 * - プログラム名一覧：`call "name"` の参照解決・補完・定義ジャンプに使います。
 * - ワールド変数型表：各ファイルの `var world.x` 定義を集約し、
 *   `world.x` 参照の型解決（worldTypes）へ供給します。
 *   キーはモデル名（`_`→`-` 写像済み）です。
 *
 * ドキュメントの open/change/close とワークスペース走査の2経路で更新します。
 */
import { DslProgram } from "./ast";
import { modelName } from "./names";
import { parse } from "./parser";
import { ValueTypeName } from "./ast";

export interface FileInfo {
    /** `program "..."` の名前です。解析失敗時は null です。 */
    programName: string | null;
    /** `var world.x: T` 定義（モデル名 → 型）です。 */
    worldDecls: Map<string, ValueTypeName>;
    /** `defarg` 宣言（名前 → 型）です。 */
    defargs: Map<string, ValueTypeName>;
}

export class WorkspaceIndex {
    private readonly files = new Map<string, FileInfo>();

    /** テキストからファイル情報を抽出します（解析失敗時は空情報）。 */
    private extract(text: string): FileInfo {
        let program: DslProgram;
        try {
            program = parse(text);
        } catch {
            return { programName: null, worldDecls: new Map(), defargs: new Map() };
        }
        const worldDecls = new Map<string, ValueTypeName>();
        const defargs = new Map<string, ValueTypeName>();
        for (const decl of program.decls) {
            if (decl.kind === "defarg") defargs.set(decl.name, decl.type);
        }
        for (const stmt of program.body) {
            if (stmt.kind === "varDecl" && stmt.world && stmt.name !== "") {
                worldDecls.set(modelName(stmt.name), stmt.type);
            }
        }
        return { programName: program.name, worldDecls, defargs };
    }

    update(uri: string, text: string) {
        this.files.set(uri, this.extract(text));
    }

    remove(uri: string) {
        this.files.delete(uri);
    }

    /** ワークスペース内の全プログラム名です。 */
    programNames(): Map<string, string> {
        const out = new Map<string, string>();
        for (const [uri, info] of this.files) {
            if (info.programName !== null) out.set(info.programName, uri);
        }
        return out;
    }

    /** 全ファイルの `var world.x` 定義を集約したワールド変数型表です。 */
    worldTypes(): Map<string, ValueTypeName> {
        const out = new Map<string, ValueTypeName>();
        for (const info of this.files.values()) {
            for (const [name, type] of info.worldDecls) {
                if (!out.has(name)) out.set(name, type);
            }
        }
        return out;
    }
}
