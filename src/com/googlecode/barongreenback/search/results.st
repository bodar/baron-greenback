$if(results)$
<div id="results">
    <table class="results $if(pager.paged)$paged$endif$ table table-striped">
        <thead>
        <tr>
            $sorter/th(headers=headers, sortedHeaders=sortedHeaders)$
        </tr>
        </thead>
        <tbody>
        $results:{ record |
            <tr>$headers:{ header | <td class="$underscores(header.name)$">$if(record.(header.name).url)$<a href='$record.(header.name).url$'>$endif$$record.(header.name).value$$if(record.(header.name).url)$</a>$endif$</td>}$ </tr>
        }$
        </tbody>
    </table>
</div>
<div class="table-actions">
    <div class="selectors">
        <a href="#" class="clearSelection">Clear Selection</a>
        <a href="#" class="selectPage">Select Page</a>
        <a href="#" class="selectAll">Select All Pages</a>
        <div class="message"></div>
    </div>
    <div class="pagination-controls">
        $pager$
    </div>
</div>

$endif$